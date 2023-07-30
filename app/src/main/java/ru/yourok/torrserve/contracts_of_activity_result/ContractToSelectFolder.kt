package ru.yourok.torrserve.contracts_of_activity_result

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract

class ContractToSelectFolder : ActivityResultContract<String?, String>() {
    override fun createIntent(context: Context, input: String?): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    override fun parseResult(resultCode: Int, intent: Intent?): String {
        if (resultCode == Activity.RESULT_OK) {
            return if (intent?.data == null) "" else intent.data.toString()
        } else return ""
    }
}