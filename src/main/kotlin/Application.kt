import dependency.Dependency
import network.startService
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

fun main() {
    setUpOutput()
    startService(Dependency)
}

private fun setUpOutput() {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))
}