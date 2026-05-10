package com.camryobd.ui

import android.graphics.Color
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
import com.camryobd.databinding.FragmentBatteryBinding
import com.camryobd.models.CellStatus
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import kotlinx.coroutines.launch

import android.content.Intent
import androidx.core.content.FileProvider
import com.camryobd.DataLogger
import java.io.File

class BatteryFragment : Fragment() {
    private var _binding: FragmentBatteryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ObdViewModel by activityViewModels()
    private lateinit var logger: DataLogger
    private var lastSavedFile: File? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBatteryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        logger = DataLogger(requireContext())
        setupChart()

        binding.btnToggleLog.setOnClickListener {
            if (logger.isCurrentlyLogging()) {
                lastSavedFile = logger.stopLogging()
                binding.btnToggleLog.text = "🔴 REC"
                binding.btnToggleLog.setTextColor(Color.rgb(255, 51, 85))
                if (lastSavedFile != null) {
                    binding.btnShareLog.visibility = View.VISIBLE
                }
            } else {
                if (logger.startLogging()) {
                    binding.btnToggleLog.text = "⏹ STOP"
                    binding.btnToggleLog.setTextColor(Color.WHITE)
                    binding.btnShareLog.visibility = View.GONE
                }
            }
        }

        binding.btnShareLog.setOnClickListener {
            lastSavedFile?.let { file ->
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Battery Log"))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.batteryState.collect { pack ->
                    updatePackView(pack)
                    if (logger.isCurrentlyLogging()) {
                        logger.logData(pack)
                    }
                }
            }
        }
    }

    private fun setupChart() {
        val chart = binding.batteryChart
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setDrawBorders(false)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.rgb(136, 136, 160)
        xAxis.setDrawGridLines(false)
        xAxis.labelCount = 14
        xAxis.granularity = 1f

        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.rgb(136, 136, 160)
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.rgb(30, 30, 40)
        // Set an arbitrary initially bounds, it auto scales anyway
        leftAxis.axisMinimum = 14f

        chart.axisRight.isEnabled = false
        chart.setTouchEnabled(false)
    }

    override fun onStart() {
        super.onStart()
        viewModel.startBatteryPolling()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopBatteryPolling()
    }

    private fun updatePackView(pack: com.camryobd.models.BatteryPackData) {
        if (pack.blocks.isEmpty()) return

        binding.packMaxValue.text = String.format("%.3f V", pack.maxVoltage)
        binding.packMinValue.text = String.format("%.3f V", pack.minVoltage)
        binding.packDeltaValue.text = String.format("%.0f mV", pack.deltaMv)
        binding.packAvgValue.text = String.format("%.3f V", pack.avgVoltage)
        binding.packTotalValue.text = String.format("%.1f V", pack.totalVoltage)

        binding.packHealthBadge.text = "● ${pack.healthStatus}"
        binding.packHealthBadge.setTextColor(when (pack.healthStatus) {
            "PERFECT" -> Color.rgb(0, 255, 136)
            "GOOD" -> Color.rgb(0, 210, 255)
            "WARNING" -> Color.rgb(255, 214, 0)
            else -> Color.rgb(255, 51, 85)
        })

        binding.balanceAnalysisText.text = pack.analysis

        val cells = pack.blocks
        val cellViews = listOf(
            binding.cell1, binding.cell2, binding.cell3, binding.cell4, binding.cell5, binding.cell6, binding.cell7,
            binding.cell8, binding.cell9, binding.cell10, binding.cell11, binding.cell12, binding.cell13, binding.cell14,
        )

        val entries = ArrayList<BarEntry>()
        val colors = ArrayList<Int>()

        for (i in cells.indices) {
            if (i >= cellViews.size) break
            val v = cellViews[i]
            val cell = cells[i]

            val bgColor = when (cell.status) {
                CellStatus.OPTIMAL -> Color.rgb(0, 60, 30)
                CellStatus.GOOD -> Color.rgb(0, 40, 70)
                CellStatus.WARN -> Color.rgb(60, 50, 0)
                CellStatus.BAD -> Color.rgb(60, 15, 25)
                CellStatus.CRITICAL -> Color.rgb(80, 10, 20)
            }
            val textColor = when (cell.status) {
                CellStatus.OPTIMAL -> Color.rgb(0, 255, 136)
                CellStatus.GOOD -> Color.rgb(0, 210, 255)
                CellStatus.WARN -> Color.rgb(255, 214, 0)
                CellStatus.BAD -> Color.rgb(255, 51, 85)
                CellStatus.CRITICAL -> Color.WHITE
            }
            v.setBackgroundColor(bgColor)
            v.setTextColor(textColor)
            v.text = String.format("%.2f", cell.voltage)
            v.contentDescription = "Block ${cell.block}: ${String.format("%.3f", cell.voltage)}V"

            // Add chart entry
            entries.add(BarEntry((i + 1).toFloat(), cell.voltage.toFloat()))
            colors.add(textColor)
        }

        // Update Chart
        val dataSet = BarDataSet(entries, "Cell Voltages")
        dataSet.colors = colors
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 8f

        val barData = BarData(dataSet)
        binding.batteryChart.data = barData
        
        // Adjust Y-axis scale based on current min/max to highlight the differences
        val min = (pack.minVoltage - 0.2).toFloat()
        val max = (pack.maxVoltage + 0.2).toFloat()
        binding.batteryChart.axisLeft.axisMinimum = min
        binding.batteryChart.axisLeft.axisMaximum = max
        
        binding.batteryChart.invalidate() // refresh chart
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
