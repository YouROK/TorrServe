package ru.yourok.torrserve.ui.fragments.speedtest

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.github.anastr.speedviewlib.TubeSpeedometer
import com.github.anastr.speedviewlib.components.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.utils.Http
import ru.yourok.torrserve.utils.Net


class SpeedTest : TSFragment() {

    private val mbps = " M" + App.context.getString(R.string.fmt_bps)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.speedtest_fragment, container, false)

        val speedometer = vi.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer)

        speedometer.unit = mbps
        speedometer.minSpeed = 0f
        speedometer.maxSpeed = 500f
        speedometer.withTremble = false
        speedometer.clearSections()
        speedometer.addSections(
            Section(0f, getPrcSpeed(50f, speedometer.maxSpeed), Color.RED),
            Section(getPrcSpeed(50f, speedometer.maxSpeed), getPrcSpeed(70f, speedometer.maxSpeed), Color.YELLOW),
            Section(getPrcSpeed(70f, speedometer.maxSpeed), getPrcSpeed(100f, speedometer.maxSpeed), Color.GREEN),
            Section(getPrcSpeed(100f, speedometer.maxSpeed), 1f, Color.BLUE)
        )
//        lifecycleScope.launch(Dispatchers.IO) {
//            setSpeedometer(501f)
//        }

        vi.findViewById<Button>(R.id.btnTestSpeed)?.setOnClickListener {
            it.isEnabled = false
            speedTest()
        }

        return vi
    }

    private fun speedTest() {
        lifecycleScope.launch(Dispatchers.IO) {
            showProgress()
            val link = Net.getHostUrl("/download/1024")
            val http = Http(Uri.parse(link))
            try {
                http.connect()
            } catch (e: Exception) {
                setStatus(e.message ?: "Error connect to server")
                hideProgress()
                return@launch
            }

            val stream = http.getInputStream()
            stream ?: let {
                setStatus("Error connect to server")
                hideProgress()
                return@launch
            }

            val b = ByteArray(1024 * 1024)
            val allSize = http.getSize()
            var readed = 0L
            try {
                while (readed < allSize) {
                    val sz = http.read(b)
                    readed += sz
                    calcSpeed(readed)
                    showProgress((readed * 100 / allSize).toInt())
                    if (!this@SpeedTest.isVisible)
                        break
                }
            } catch (e: Exception) {
                setStatus(e.message ?: "Error read from server")
            }

            hideProgress()

            withContext(Dispatchers.Main) {
                view?.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer)?.speedTo(0.0f)
                view?.findViewById<Button>(R.id.btnTestSpeed)?.isEnabled = true
            }
        }
    }

    private suspend fun setStatus(st: String) {
        withContext(Dispatchers.Main) {
            val status = view?.findViewById<TextView>(R.id.tvSPStatus) ?: return@withContext
            status.text = st
        }
    }

    private var lastCheck = System.currentTimeMillis()
    private var lastReaded = 0L

    private suspend fun calcSpeed(readed: Long) {
        val time = System.currentTimeMillis() - lastCheck
        if (time > 1000) {
            val dReaded = readed - lastReaded

            val speed = dReaded.toFloat() / time.toFloat() * 0.008f // MBit / sec
            setSpeed(speed)
            setSpeedometer(speed)

            lastCheck = System.currentTimeMillis()
            lastReaded = readed
        }
    }

    private var maxSpeed = 0f

    private suspend fun setSpeed(speed: Float) {
        if (speed > maxSpeed)
            maxSpeed = speed
        withContext(Dispatchers.Main) {
            val ms = String.format("%.1f$mbps", maxSpeed)
            view?.findViewById<TextView>(R.id.tvSPStatus)?.text = ms
            view?.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer)?.speedTo(speed)
        }
    }


    private suspend fun setSpeedometer(speed: Float) {
        withContext(Dispatchers.Main) {
            val speedometer = view?.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer) ?: return@withContext
            if (speed > speedometer.maxSpeed) {
                if (speed < 100f) {
//                    speedometer.maxSpeed = 100f
                    setMaxSpeed(speedometer.maxSpeed, 100f) {
                        speedometer.clearSections()
                        speedometer.addSections(
                            Section(0f, getPrcSpeed(50f, speedometer.maxSpeed), Color.RED),
                            Section(getPrcSpeed(50f, speedometer.maxSpeed), getPrcSpeed(70f, speedometer.maxSpeed), Color.YELLOW),
                            Section(getPrcSpeed(70f, speedometer.maxSpeed), 1f, Color.GREEN)
                        )
                    }
                }
                if (speed > 100f && speed < 300f) {
//                    speedometer.maxSpeed = 300f
                    setMaxSpeed(speedometer.maxSpeed, 300f) {
                        speedometer.clearSections()
                        speedometer.addSections(
                            Section(0f, getPrcSpeed(50f, speedometer.maxSpeed), Color.RED),
                            Section(getPrcSpeed(50f, speedometer.maxSpeed), getPrcSpeed(70f, speedometer.maxSpeed), Color.YELLOW),
                            Section(getPrcSpeed(70f, speedometer.maxSpeed), getPrcSpeed(100f, speedometer.maxSpeed), Color.GREEN),
                            Section(getPrcSpeed(100f, speedometer.maxSpeed), 1f, Color.BLUE)
                        )
                    }
                }
                if (speed > 300f && speed < 500f) {
                    speedometer.maxSpeed = 500f
                    setMaxSpeed(speedometer.maxSpeed, 500f) {
                        speedometer.clearSections()
                        speedometer.addSections(
                            Section(0f, getPrcSpeed(50f, speedometer.maxSpeed), Color.RED),
                            Section(getPrcSpeed(50f, speedometer.maxSpeed), getPrcSpeed(70f, speedometer.maxSpeed), Color.YELLOW),
                            Section(getPrcSpeed(70f, speedometer.maxSpeed), getPrcSpeed(100f, speedometer.maxSpeed), Color.GREEN),
                            Section(getPrcSpeed(100f, speedometer.maxSpeed), 1f, Color.BLUE)
                        )
                    }
                }
                if (speed > 500f && speed < 1000f) {
//                    speedometer.maxSpeed = 1000f
                    setMaxSpeed(speedometer.maxSpeed, 1000f) {
                        speedometer.clearSections()
                        speedometer.addSections(
                            Section(0f, getPrcSpeed(50f, speedometer.maxSpeed), Color.RED),
                            Section(getPrcSpeed(50f, speedometer.maxSpeed), getPrcSpeed(70f, speedometer.maxSpeed), Color.YELLOW),
                            Section(getPrcSpeed(70f, speedometer.maxSpeed), getPrcSpeed(100f, speedometer.maxSpeed), Color.GREEN),
                            Section(getPrcSpeed(100f, speedometer.maxSpeed), 1f, Color.BLUE)
                        )
                    }
                }
                if (speed > 1000f) {
                    setMaxSpeed(speedometer.maxSpeed, 5000f) {
                        speedometer.clearSections()
                        speedometer.addSections(
                            Section(0f, getPrcSpeed(50f, speedometer.maxSpeed), Color.RED),
                            Section(getPrcSpeed(50f, speedometer.maxSpeed), getPrcSpeed(70f, speedometer.maxSpeed), Color.YELLOW),
                            Section(getPrcSpeed(70f, speedometer.maxSpeed), getPrcSpeed(100f, speedometer.maxSpeed), Color.GREEN),
                            Section(getPrcSpeed(100f, speedometer.maxSpeed), 1f, Color.BLUE)
                        )
                    }
                }
            }
        }
    }

    private suspend fun setMaxSpeed(oldSpeed: Float, maxSpeed: Float, onEnd: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in oldSpeed.toInt()..maxSpeed.toInt() step 10) {
                withContext(Dispatchers.Main) {
                    view?.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer)?.maxSpeed = i.toFloat()
                }
                delay(50)
            }
            withContext(Dispatchers.Main) {
                view?.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer)?.maxSpeed = maxSpeed
            }
            withContext(Dispatchers.Main) {
                onEnd()
            }
        }
    }

    private fun getPrcSpeed(sp: Float, max: Float): Float {
        return sp / max
    }
}
