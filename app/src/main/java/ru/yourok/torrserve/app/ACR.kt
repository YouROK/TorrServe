package ru.yourok.torrserve.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import ru.yourok.torrserve.activitys.ACRActivity
import java.io.*
import java.util.*

/**
 * @author Deven
 *
 * Licensed under the Apache License 2.0 license see:
 * http://www.apache.org/licenses/LICENSE-2.0
 */
class ACR private constructor(private val application: Application) : Thread.UncaughtExceptionHandler {

    private var startAttempted = false

    private var versionName: String? = null
    //private String buildNumber;
    private var packageName: String? = null
    private var filePath: String? = null
    private var phoneModel: String? = null
    private var androidVersion: String? = null
    private var board: String? = null
    private var brand: String? = null
    private var device: String? = null
    private var display: String? = null
    private var fingerPrint: String? = null
    private var host: String? = null
    private var id: String? = null
    private var manufacturer: String? = null
    private var model: String? = null
    private var product: String? = null
    private var tags: String? = null
    private var time: Long = 0
    private var type: String? = null
    private var user: String? = null
    private val customParameters = HashMap<String, String>()

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private val availableInternalMemorySize: Long
        get() {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.getPath())
            val blockSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                stat.blockSizeLong
            else
                stat.blockSize.toLong()

            val availableBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                stat.availableBlocksLong
            else
                stat.availableBlocks.toLong()

            return availableBlocks * blockSize / (1024 * 1024)
        }

    private val totalInternalMemorySize: Long
        get() {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.getPath())
            val blockSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                stat.blockSizeLong
            else
                stat.blockSize.toLong()

            val totalBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                stat.blockCountLong
            else
                stat.blockCount.toLong()
            return totalBlocks * blockSize / (1024 * 1024)
        }

    private// Try to create the files folder if it doesn't exist
    // Filter for ".stacktrace" files
    val errorFileList: Array<String>
        get() {
            val dir = File(filePath!! + "/")
            dir.mkdir()
            val filter = object : FilenameFilter {
                override fun accept(dir: File, name: String): Boolean {
                    return name.endsWith(".stacktrace")
                }
            }
            return dir.list(filter)
        }

    fun start() {
        if (startAttempted) {
            showLog("Already started")
            return
        }
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)

        startAttempted = true
    }

    /**
     * (Required) Defines one or more email addresses to send bug reports to. This method MUST be
     * called before calling start This method CANNOT be called after calling
     * start.
     *
     * @param emailAddresses one or more email addresses
     * @return the current AutoErrorReporterinstance (to allow for method chaining)
     */

    fun setEmailAddresses(vararg emailAddresses: String): ACR {
        if (startAttempted) {
            throw IllegalStateException(
                    "EmailAddresses must be set before start")
        }
        recipients = emailAddresses.toList().toTypedArray()
        return this
    }

    /**
     * (Optional) Defines a custom subject line to use for all bug reports. By default, reports will
     * use the string defined in DEFAULT_EMAIL_SUBJECT This method CANNOT be called
     * after calling start.
     * @param emailSubject custom email subject line
     * @return the current AutoErrorReporter instance (to allow for method chaining)
     */
    fun setEmailSubject(emailSubject: String): ACR {
        if (startAttempted) {
            throw IllegalStateException("EmailSubject must be set before start")
        }

        DEFAULT_EMAIL_SUBJECT = emailSubject
        return this
    }

    fun addCustomData(Key: String, Value: String) {
        customParameters.put(Key, Value)
    }

    private fun createCustomInfoString(): String {
        var customInfo = ""
        for (currentKey in customParameters.keys) {
            val currentVal = customParameters[currentKey]
            customInfo += currentKey + " = " + currentVal + "\n"
        }
        return customInfo
    }

    private fun recordInformations(context: Context) {
        try {
            val pm = context.getPackageManager()
            val pi: PackageInfo
            // Version
            pi = pm.getPackageInfo(context.getPackageName(), 0)
            versionName = pi.versionName
            //buildNumber = currentVersionNumber(context);
            // Package name
            packageName = pi.packageName

            // Device model
            phoneModel = Build.MODEL
            // Android version
            androidVersion = Build.VERSION.RELEASE

            board = Build.BOARD
            brand = Build.BRAND
            device = Build.DEVICE
            display = Build.DISPLAY
            fingerPrint = Build.FINGERPRINT
            host = Build.HOST
            id = Build.ID
            model = Build.MODEL
            product = Build.PRODUCT
            manufacturer = Build.MANUFACTURER
            tags = Build.TAGS
            time = Build.TIME
            type = Build.TYPE
            user = Build.USER

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun createInformationString(): String {
        recordInformations(application)
        val infoStringBuffer = StringBuilder()
        infoStringBuffer.append("\nVERSION		: ").append(versionName)
        infoStringBuffer.append("\nPACKAGE      : ").append(packageName)
        infoStringBuffer.append("\nFILE-PATH    : ").append(filePath)
        infoStringBuffer.append("\nPHONE-MODEL  : ").append(phoneModel)
        infoStringBuffer.append("\nANDROID_VERS : ").append(androidVersion)
        infoStringBuffer.append("\nBOARD        : ").append(board)
        infoStringBuffer.append("\nBRAND        : ").append(brand)
        infoStringBuffer.append("\nDEVICE       : ").append(device)
        infoStringBuffer.append("\nDISPLAY      : ").append(display)
        infoStringBuffer.append("\nFINGER-PRINT : ").append(fingerPrint)
        infoStringBuffer.append("\nHOST         : ").append(host)
        infoStringBuffer.append("\nID           : ").append(id)
        infoStringBuffer.append("\nMODEL        : ").append(model)
        infoStringBuffer.append("\nPRODUCT      : ").append(product)
        infoStringBuffer.append("\nMANUFACTURER : ").append(manufacturer)
        infoStringBuffer.append("\nTAGS         : ").append(tags)
        infoStringBuffer.append("\nTIME         : ").append(time)
        infoStringBuffer.append("\nTYPE         : ").append(type)
        infoStringBuffer.append("\nUSER         : ").append(user)
        infoStringBuffer.append("\nTOTAL-INTERNAL-MEMORY     : ").append(totalInternalMemorySize.toString() + " mb")
        infoStringBuffer.append("\nAVAILABLE-INTERNAL-MEMORY : ").append(availableInternalMemorySize.toString() + " mb")

        return infoStringBuffer.toString()
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        showLog("====uncaughtException")

        val reportStringBuffer = StringBuilder()
        reportStringBuffer.append("Error Report collected on : ").append(Date().toString())
        reportStringBuffer.append("\n\nInformations :\n==============")
        reportStringBuffer.append(createInformationString())
        val customInfo = createCustomInfoString()
        if (customInfo != "") {
            reportStringBuffer.append("\n\nCustom Informations :\n==============\n")
            reportStringBuffer.append(customInfo)
        }

        reportStringBuffer.append("\n\nStack :\n==============\n")
        val result = StringWriter()
        val printWriter = PrintWriter(result)
        e.printStackTrace(printWriter)
        reportStringBuffer.append(result.toString())

        reportStringBuffer.append("\nCause :\n==============")
        // If the exception was thrown in a background thread inside
        // AsyncTask, then the actual exception can be found with getCause
        var cause: Throwable? = e.cause
        while (cause != null) {
            cause.printStackTrace(printWriter)
            reportStringBuffer.append(result.toString())
            cause = cause.cause
        }
        printWriter.close()

        reportStringBuffer.append("\n\n**** End of current Report ***")
        showLog("====uncaughtException \n Report: " + reportStringBuffer.toString())
        saveAsFile(reportStringBuffer.toString())

        checkError(application)

        val intent = Intent(application, ACRActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("report", wholeErrorText)
        application.startActivity(intent)


//        checkErrorAndSendMail(application)

        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(10)
    }

    private fun saveAsFile(errorContent: String) {
        showLog("====SaveAsFile")
        try {
            val generator = Random()
            val random = generator.nextInt(99999)
            val FileName = "stack-$random.stacktrace"
            val trace = application.openFileOutput(FileName,
                    Context.MODE_PRIVATE)
            trace.write(errorContent.toByteArray())
            trace.close()
        } catch (e: Exception) {
            // ...
        }

    }

    private fun bIsThereAnyErrorFile(): Boolean {
        return errorFileList.size > 0
    }

    internal fun checkError(_context: Context) {
        try {
            filePath = _context.getFilesDir().getAbsolutePath()
            if (bIsThereAnyErrorFile()) {
                val wholeErrorTextSB = StringBuilder()

                val errorFileList = errorFileList
                var curIndex = 0
                val maxSendMail = 5
                for (curString in errorFileList) {
                    if (curIndex++ <= maxSendMail) {
                        wholeErrorTextSB.append("New Trace collected :\n=====================\n")
                        val filePathStr = filePath + "/" + curString
                        val input = BufferedReader(FileReader(filePathStr))
                        wholeErrorTextSB.append(input.use { it.readText() })
                        input.close()
                    }

                    // DELETE FILES !!!!
                    val curFile = File(filePath + "/" + curString)
                    curFile.delete()
                }
                wholeErrorText = wholeErrorTextSB.toString()
//                sendErrorMail(_context, wholeErrorTextSB.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun showLog(msg: String) {
        if (DEBUGABLE) Log.i(TAG, msg)
    }

    companion object {
        private val TAG = this::class.java.simpleName
        private val DEBUGABLE = false
        private var DEFAULT_EMAIL_SUBJECT = "ACR: New Crash Report Generated"
        private var sInstance: ACR? = null
        private var recipients: Array<String>? = null

        var wholeErrorText: String = ""

        operator fun get(application: Application): ACR {
            if (sInstance == null)
                sInstance = ACR(application)
            return sInstance as ACR
        }

        fun sendErrorMail(_context: Context, errorContent: String) {
            if (DEBUGABLE) Log.i(TAG, "====sendErrorMail")
            val sendIntent = Intent(Intent.ACTION_SEND)
            val subject = DEFAULT_EMAIL_SUBJECT
            val body = "\n\n" + errorContent + "\n\n"
            sendIntent.putExtra(Intent.EXTRA_EMAIL, recipients)
            sendIntent.putExtra(Intent.EXTRA_TEXT, body)
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
            sendIntent.setType("message/rfc822")
            sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val chooser = Intent.createChooser(sendIntent, "Send TorrServe crash report")
            chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            _context.startActivity(chooser)
        }

    }

}
