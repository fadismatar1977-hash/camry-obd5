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
import com.camryobd.ConnectionState
import com.camryobd.ObdViewModel
import com.camryobd.databinding.FragmentConnectionBinding
import kotlinx.coroutines.launch

class ConnectionFragment : Fragment() {
    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ObdViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.connectButton.setOnClickListener {
            val address = binding.ipInput.text.toString().trim()
            if (address.isNotEmpty()) {
                viewModel.connect(address)
            } else {
                binding.statusText.text = "Enter IP address (e.g. 192.168.0.10)"
                binding.statusText.visibility = View.VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.connectionState.collect { state ->
                    when (state) {
                        is ConnectionState.Disconnected -> {
                            binding.connectButton.isEnabled = true
                            binding.statusText.text = "Disconnected"
                        }
                        is ConnectionState.Connecting -> {
                            binding.connectButton.isEnabled = false
                            binding.statusText.text = "Connecting..."
                            binding.statusText.visibility = View.VISIBLE
                        }
                        is ConnectionState.Initializing -> {
                            binding.connectButton.isEnabled = false
                            binding.statusText.text = "Initializing ELM327..."
                        }
                        is ConnectionState.Connected -> {
                            binding.connectButton.isEnabled = true
                            binding.statusText.text = "Connected ✓"
                            val activity = requireActivity() as? com.camryobd.MainActivity
                            activity?.switchToDashboard()
                        }
                        is ConnectionState.Error -> {
                            binding.connectButton.isEnabled = true
                            binding.statusText.text = state.message
                            binding.statusText.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
