package it.vfsfitvnm.vimusic.prototype

fun main() {
    val tests = StreamSnapshotTest()
    tests.preservesStreamFieldsButRedactsDiagnosticString()
    tests.explicitlyFlagsBothBoundedAndChunkedStreams()
    tests.cacheExpiresAndDoesNotCrossSessionGenerations()
    tests.unknownExpiryAndInvalidationDoNotReuseUrls()
    println("PASS: 4 StreamSnapshot/StreamCache tests with test-only stubs")
}
