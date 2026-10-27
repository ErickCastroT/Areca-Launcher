package com.areca.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Soporte de paquetes de iconos estilo ADW/Nova (appfilter.xml). */
class IconPackManager(private val context: Context) {

    private class Pack(val resources: Resources, val packageName: String, val drawables: Map<String, String>)

    private val loaded = HashMap<String, Pack?>()

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        return ACTIONS
            .flatMap { pm.queryIntentActivities(Intent(it), PackageManager.GET_META_DATA) }
            .map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Icono del paquete para el componente, o null si no lo define. */
    fun iconFor(packPackage: String, component: ComponentName): Drawable? {
        val name = pack(packPackage)?.drawables?.get(component.flattenToString()) ?: return null
        return drawableByName(packPackage, name)
    }

    /** Nombres de todos los iconos que declara el paquete (para elegir uno a mano). */
    fun allIcons(packPackage: String): List<String> =
        pack(packPackage)?.drawables?.values?.distinct()?.sorted() ?: emptyList()

    fun drawableByName(packPackage: String, name: String): Drawable? {
        val pack = pack(packPackage) ?: return null
        val id = pack.resources.getIdentifier(name, "drawable", pack.packageName)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(pack.resources, id, null) }.getOrNull()
    }

    private fun pack(packPackage: String): Pack? =
        synchronized(loaded) { loaded.getOrPut(packPackage) { load(packPackage) } }

    private fun load(packageName: String): Pack? = runCatching {
        val res = context.packageManager.getResourcesForApplication(packageName)
        val parser = openAppFilter(res, packageName) ?: return null
        val map = HashMap<String, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
            val component = parser.getAttributeValue(null, "component") ?: continue
            val drawable = parser.getAttributeValue(null, "drawable") ?: continue
            val inner = COMPONENT_INFO.find(component)?.groupValues?.get(1) ?: continue
            ComponentName.unflattenFromString(inner)?.let { map[it.flattenToString()] = drawable }
        }
        Pack(res, packageName, map)
    }.getOrNull()

    private fun openAppFilter(res: Resources, packageName: String): XmlPullParser? {
        val xmlId = res.getIdentifier("appfilter", "xml", packageName)
        if (xmlId != 0) return res.getXml(xmlId)
        return runCatching {
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(res.assets.open("appfilter.xml"), null)
            }
        }.getOrNull()
    }

    private companion object {
        val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME",
            "com.gau.go.launcherex.theme",
        )
        val COMPONENT_INFO = Regex("""ComponentInfo\{(.+)\}""")
    }
}
