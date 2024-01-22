import android.content.Context
import android.content.Intent
import android.net.Uri
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.feature.intent.processing.IntentProcessor
import mozilla.components.support.ktx.android.net.getFileName
import java.io.File
import java.io.FileOutputStream


class AddonInstallIntentProcessor(private val context: Context, private val runtime: WebExtensionRuntime) : IntentProcessor {
    override fun process(intent: Intent): Boolean {
        if(intent.data == null) {
            return false
        }
        val iuri = intent.data as Uri
        if(!iuri.scheme.equals("content")) {
            return false
        }
        val file = fromUri(iuri)
        val extURI = parseExtension(file)
        installExtension(extURI) {}
        return true
    }

    fun installExtension(b64: String, onSuccess: ((WebExtension) -> Unit)) {
        runtime.installWebExtension(b64, InstallationMethod.FROM_FILE, onSuccess)
    }

    fun parseExtension(inp: File): String {
        return Uri.fromFile(inp.absoluteFile).toString()
    }

    fun fromUri(uri: Uri): File {
        val name = uri.getFileName(context.contentResolver)
        val file = File(context.externalCacheDir, name)
        file.createNewFile()
        val ostream = FileOutputStream(file.absolutePath)
        val istream = context.contentResolver.openInputStream(uri)!!
        istream.copyTo(ostream)
        ostream.close()
        istream.close()
        return file
    }
}
