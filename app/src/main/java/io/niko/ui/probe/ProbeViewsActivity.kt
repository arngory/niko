package io.niko.ui.probe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.niko.R
import io.niko.databinding.ProbeSimpleBinding
import io.niko.databinding.ProbeTileBinding

/** The UI probe's Views half (see [ProbeContent]): res/layout/probe_simple.xml. */
class ProbeViewsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_SagerNet_Niko)
        super.onCreate(savedInstanceState)
        val binding = ProbeSimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.probeWordmark.text = ProbeContent.WORDMARK
        binding.probeSimple.text = ProbeContent.SIMPLE
        binding.probeAdvanced.text = ProbeContent.ADVANCED
        binding.probeStatus.text = ProbeContent.STATUS
        binding.probeDetail.text = ProbeContent.DETAIL
        tile(binding.probeTileConnection, R.drawable.ic_l_plug, ProbeContent.CONNECTION_LABEL, ProbeContent.CONNECTION_VALUE)
        tile(binding.probeTileMode, R.drawable.ic_l_shield, ProbeContent.MODE_LABEL, ProbeContent.MODE_VALUE)
        tile(binding.probeTileRoute, R.drawable.ic_l_route, ProbeContent.ROUTE_LABEL, ProbeContent.ROUTE_VALUE)
        binding.probeNodeName.text = ProbeContent.NODE
        binding.probeNodeUntil.text = ProbeContent.NODE_UNTIL
        binding.probeNodeTraffic.text = ProbeContent.NODE_TRAFFIC
        binding.probeNodePing.text = ProbeContent.NODE_PING
        binding.probeConnect.setOnClickListener { }
        binding.probeNode.setOnClickListener { }
        listOf(binding.probeTileConnection, binding.probeTileMode, binding.probeTileRoute).forEach {
            it.root.setOnClickListener { }
        }
    }

    private fun tile(tile: ProbeTileBinding, icon: Int, label: String, value: String) {
        tile.probeTileIcon.setImageResource(icon)
        tile.probeTileLabel.text = label
        tile.probeTileValue.text = value
    }
}
