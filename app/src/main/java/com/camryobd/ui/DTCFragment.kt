package com.camryobd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.camryobd.OBD2Manager
import com.camryobd.ObdViewModel
import com.camryobd.databinding.FragmentDtcBinding
import kotlinx.coroutines.launch

class DTCFragment : Fragment() {
    private var _binding: FragmentDtcBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ObdViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDtcBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.scanDtcButton.setOnClickListener { viewModel.scanDTCs() }
        binding.clearDtcButton.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("تنبيه هام")
                .setMessage("مسح كود العطل سيقوم فقط بإطفاء لمبة المحرك مؤقتاً وإعادة ضبط حساسات الفحص، ولكنه لن يصلح المشكلة الميكانيكية أو الكهربائية الفعلية في السيارة.\n\nإذا لم يتم إصلاح الخلل (مثل ضعف خلية في بطارية الهايبرد)، سيعود الكود للظهور مجدداً بمجرد قيادة السيارة.\n\nهل أنت متأكد أنك تريد مسح الكود؟")
                .setPositiveButton("نعم، امسح الكود") { _, _ ->
                    viewModel.clearDTCs()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dtcMessage.collect { msg ->
                        if (msg.isNotEmpty()) {
                            binding.dtcResultText.text = msg
                            binding.dtcResultText.visibility = View.VISIBLE
                        }
                    }
                }
                
                launch {
                    viewModel.dtcState.collect { codes ->
                        if (codes != null && codes.isNotEmpty()) {
                            val sb = StringBuilder()
                            for (code in codes) {
                                sb.appendLine(code.code)
                                val desc = OBD2Manager.DTC_DESCRIPTIONS[code.code]
                                if (desc != null) sb.appendLine("  → $desc")
                            }
                            binding.dtcResultText.text = sb.toString()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.dtcState.value == null) {
            viewModel.scanDTCs()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
