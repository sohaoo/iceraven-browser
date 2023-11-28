import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.forkmaintainers.iceraven.components.getSafeString
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.intent.processing.IntentProcessor
import mozilla.components.support.ktx.android.net.getFileName
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.ZipFile


class AddonInstallIntentProcessor(private val context: Context, private val engine: Engine) : IntentProcessor {
	override fun process(intent: Intent): Boolean {
		val iuri = fromUri(intent.data as Uri)
        if(iuri == null) {
            return false
        }
        val ext = iuri.let { parseExtension(it) }
        installExtension(ext.get(0), ext.get(1))
		return true
	}
    fun installExtension(id: String, b64: String) {
        engine.installWebExtension(id, b64)

    }
    fun parseExtension(inp: File): List<String> {
        val file = ZipFile(inp)
        val mis = file.getInputStream(file.getEntry("manifest.json"))
        val t = org.json.JSONObject(String(mis.readBytes()))
        val al = ArrayList<String>()
        val bss = try {
            t.getJSONObject("browser_specific_settings")
        } catch(e:JSONException) {
            t.getJSONObject("applications")
        }
        al.add(bss.getJSONObject("gecko").getSafeString("id") )
        al.add(Uri.fromFile(inp.absoluteFile).toString())
        file.close()
        mis.close()
        return al
    }
    fun fromUri(uri: Uri): File? {
        val name = uri.getFileName(context.contentResolver)
        val file: File = File(context.externalCacheDir, name)
        file.createNewFile()
        val ostream = FileOutputStream(file.absolutePath)
        val istream = context.contentResolver.openInputStream(uri)!!
        istream.copyTo(ostream)
        ostream.close()
        istream.close()
        return file
    }
}
