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
import com.camryobd.ObdViewModel
import com.camryobd.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ObdViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dashboardState.collect { state ->
                        binding.rpmValue.text = "${state.rpm.value} ${state.rpm.unit}"
                        binding.speedValue.text = "${state.speed.value} ${state.speed.unit}"
                        binding.coolantValue.text = "${state.coolant.value} ${state.coolant.unit}"
                        binding.batteryValue.text = "${state.battery12V.value} ${state.battery12V.unit}"
                        binding.fuelValue.text = state.fuel.value
                        binding.loadValue.text = state.load.value
                        binding.hvSocValue.text = state.hybridData.hvBatterySoc
                        binding.hvVoltageValue.text = state.hybridData.hvBatteryVoltage
                        binding.hvTempValue.text = state.hybridData.hvBatteryTemp
                        binding.evPctValue.text = state.evPercentage
                        binding.timerValue.text = state.timer0to100
                    }
                }
                
                launch {
                    viewModel.dtcState.collect { codes ->
                        if (codes != null && codes.isNotEmpty()) {
                            binding.dtcWarningBanner.visibility = View.VISIBLE
                            binding.dtcWarningText.text = "${codes.size} Fault Code(s) Detected: " + codes[0].code
                        } else {
                            binding.dtcWarningBanner.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startDashboardPolling()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopDashboardPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
