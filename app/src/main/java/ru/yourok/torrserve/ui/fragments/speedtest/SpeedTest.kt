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
import com.github.anastr.speedviewlib.Speedometer
import com.github.anastr.speedviewlib.TubeSpeedometer
import com.github.anastr.speedviewlib.components.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.utils.Http
import ru.yourok.torrserve.utils.Net


class SpeedTest : TSFragment() {

    private val mbps = " M" + App.context.getString(R.string.fmt_bps)
    private var lastCheck = System.currentTimeMillis()
    private var lastReaded = 0L
    private var maxSpeed = 0f
    private var averageSpeed = 0f
    private var isStop = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.speedtest_fragment, container, false)

        val speedometer = vi.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer)

        speedometer.unit = mbps
        speedometer.minSpeed = 0f
        speedometer.maxSpeed = 100f
        speedometer.withTremble = false
        speedometer.speedometerMode = Speedometer.Mode.NORMAL // TOP

        vi.findViewById<Button>(R.id.btn100mb)?.setOnClickListener {
            vi?.findViewById<Button>(R.id.btn100mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn500mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn1000mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn5000mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btnStop)?.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.Main) {
                speedTest(100)
            }
        }

        vi.findViewById<Button>(R.id.btn500mb)?.setOnClickListener {
            vi?.findViewById<Button>(R.id.btn100mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn500mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn1000mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn5000mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btnStop)?.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.Main) {
                speedTest(500)
            }
        }

        vi.findViewById<Button>(R.id.btn1000mb)?.setOnClickListener {
            vi?.findViewById<Button>(R.id.btn100mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn500mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn1000mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn5000mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btnStop)?.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.Main) {
                speedTest(1000)
            }
        }

        vi.findViewById<Button>(R.id.btn5000mb)?.setOnClickListener {
            vi?.findViewById<Button>(R.id.btn100mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn500mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn1000mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btn5000mb)?.isEnabled = false
            vi?.findViewById<Button>(R.id.btnStop)?.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.Main) {
                speedTest(5000)
            }
        }

        vi.findViewById<Button>(R.id.btnStop)?.apply {
            setOnClickListener {
                isStop = true
            }
            visibility = View.INVISIBLE
        }

        return vi
    }

    private suspend fun speedTest(sizeMB: Int) {
        setSpeedometer(sizeMB)
        averageSpeed = 0f
        maxSpeed = 0f
        lastReaded = 0
        lastCheck = System.currentTimeMillis()
        isStop = false

        lifecycleScope.launch(Dispatchers.IO) {
            val link = Net.getHostUrl("/download/$sizeMB")
            val http = Http(Uri.parse(link))
            try {
                http.setAuth(Net.getAuthB64())
                http.connect()
            } catch (e: Exception) {
                setStatus(e.message ?: "Error connect to server")
                return@launch
            }

            val stream = http.getInputStream()
            stream ?: let {
                setStatus("Error connect to server")
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
                    if (!this@SpeedTest.isVisible || isStop)
                        break
                }
            } catch (e: Exception) {
                setStatus(e.message ?: "Error read from server")
            }

            withContext(Dispatchers.Main) {
                view?.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer)?.speedTo(0.0f)
                view?.findViewById<Button>(R.id.btn100mb)?.isEnabled = true
                view?.findViewById<Button>(R.id.btn500mb)?.isEnabled = true
                view?.findViewById<Button>(R.id.btn1000mb)?.isEnabled = true
                view?.findViewById<Button>(R.id.btn5000mb)?.isEnabled = true
                view?.findViewById<Button>(R.id.btnStop)?.visibility = View.INVISIBLE
            }
        }
    }

    private suspend fun setStatus(st: String) {
        withContext(Dispatchers.Main) {
            val status = view?.findViewById<TextView>(R.id.tvSPStatus) ?: return@withContext
            status.text = st
        }
    }

    private suspend fun calcSpeed(readed: Long) {
        val time = System.currentTimeMillis() - lastCheck
        if (time > 1000) {
            val dReaded = readed - lastReaded

            val speed = dReaded.toFloat() / time.toFloat() * 0.008f // MBit/sec
            setSpeed(speed)

            lastCheck = System.currentTimeMillis()
            lastReaded = readed
        }
    }

    private suspend fun setSpeed(speed: Float) {
        if (speed > maxSpeed)
            maxSpeed = speed

        if (averageSpeed == 0f)
            averageSpeed = speed
        else {
            averageSpeed += speed
            averageSpeed /= 2f
        }
        withContext(Dispatchers.Main) {
            val ms = String.format("%s %.1f$mbps | %s %.1f$mbps", getString(R.string.avg), averageSpeed, getString(R.string.max), maxSpeed)
            view?.findViewById<TextView>(R.id.tvSPStatus)?.text = ms
            view?.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer)?.speedTo(speed)
        }
    }


    private suspend fun setSpeedometer(sizeMB: Int) {
        when (sizeMB) {
            100 -> {
                setSection(100f)
            }

            500 -> {
                setSection(500f)
            }

            1000 -> {
                setSection(1000f)
            }

            5000 -> {
                setSection(5000f)
            }
        }
    }

    private suspend fun setSection(maxSpeed: Float) {
        withContext(Dispatchers.Main) {
            val speedometer = view?.findViewById<TubeSpeedometer>(R.id.tubeSpeedometer) ?: return@withContext
            speedometer.maxSpeed = maxSpeed
            speedometer.clearSections()
            if (maxSpeed <= 100) {
                speedometer.addSections(
                    Section(0f, getPrcSpeed(50f, speedometer.maxSpeed), Color.RED),
                    Section(getPrcSpeed(50f, speedometer.maxSpeed), getPrcSpeed(70f, speedometer.maxSpeed), Color.YELLOW),
                    Section(getPrcSpeed(70f, speedometer.maxSpeed), 1f, Color.parseColor("#00af50"))
                )
            } else {
                speedometer.addSections(
                    Section(0f, getPrcSpeed(50f, speedometer.maxSpeed), Color.RED),
                    Section(getPrcSpeed(50f, speedometer.maxSpeed), getPrcSpeed(70f, speedometer.maxSpeed), Color.YELLOW),
                    Section(getPrcSpeed(70f, speedometer.maxSpeed), getPrcSpeed(100f, speedometer.maxSpeed), Color.parseColor("#00af50")),
                    Section(getPrcSpeed(100f, speedometer.maxSpeed), 1f, Color.BLUE)
                )
            }
        }
    }

    private fun getPrcSpeed(sp: Float, max: Float): Float {
        return sp / max
    }
}
