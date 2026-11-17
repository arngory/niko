package io.niko.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import io.niko.R
import io.niko.databinding.LayoutStunBinding
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import libcore.Libcore

class StunActivity : ThemedActivity() {

    private lateinit var binding: LayoutStunBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutStunBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.stun_test)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_l_arrow_left)
        }
        binding.stunTest.setOnClickListener {
            doTest()
        }
    }

    fun doTest() {
        binding.waitLayout.isVisible = true
        runOnDefaultDispatcher {
            val result = try {
                val _result = Libcore.stunTest(binding.natStunServer.text.toString())
                if (_result!!.success) {
                    _result.text
                } else {
                    throw Exception(_result.text)
                }
            } catch (e: Exception) {
                onMainDispatcher {
                    AlertDialog.Builder(this@StunActivity)
                        .setTitle(R.string.error_title)
                        .setMessage(e.readableMessage)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            finish()
                        }
                        .setOnCancelListener {
                            finish()
                        }
                        .runCatching { show() }
                }
                return@runOnDefaultDispatcher
            }
            onMainDispatcher {
                binding.waitLayout.isVisible = false
                binding.natResult.text = result
            }
        }
    }

}