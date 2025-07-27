package alls.tech.gr.manager.installation.steps

import android.content.Context
import alls.tech.gr.manager.installation.BaseStep
import alls.tech.gr.manager.installation.Print
import alls.tech.gr.manager.utils.KeyStoreUtils
import java.io.File

class SignClonedGrindrApk(val keyStoreUtils: KeyStoreUtils, val outputDir: File): BaseStep() {
    override suspend fun doExecute(
        context: Context,
        print: Print,
    ) {
        for (file in outputDir.listFiles()!!) {
            if (!file.name.endsWith(".apk")) {
                print("Skipping ${file.name} as it is not an APK")
                continue
            }

//            val outFile = File(outputDir, "${file.name}-aligned.apk")
//            val zipIn = RandomAccessFile(file, "r");
//            val zipOut = outFile.outputStream();
//
//            print("Aligning ${file.name}...")
//
//            ZipAlign.alignZip(zipIn, zipOut)
//
//            print("Signing ${outFile.name}...")

            try {
                keyStoreUtils.signApk(file, File(outputDir, "${file.name}-signed.apk"))
                //outFile.delete()
                file.delete()
            } catch (e: Exception) {
                print("Failed to sign ${file.name}: ${e.localizedMessage}")
                throw e
            }
        }
    }

    override val name: String = "Sign cloned Grindr APK"
}