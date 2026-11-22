package io.ymusic.ui

import io.ymusic.remoteconfig.RemoteConfigData

/** The app's links and contact address: the remote config's value when it sends one, else the built-in one. */
internal class AppLinks(config: RemoteConfigData?) {
    val website: String = config?.features?.websiteUrl ?: "https://github.com/openadze/Verre"
    val telegram: String = config?.features?.telegramUrl ?: "https://github.com/openadze/Verre/discussions"
    val feedbackEmail: String = config?.features?.feedbackEmail ?: "feedback@verre.invalid" // TODO(verre): set a real address
    val feedbackIssue: String = config?.features?.feedbackIssueUrl ?: "https://github.com/openadze/Verre/issues/new"
    val sourceCode: String = config?.features?.sourceCodeUrl ?: "https://github.com/openadze/Verre"
    val terms: String = config?.features?.termsUrl ?: "https://github.com/openadze/Verre/blob/verre/TERMS.md"
    val changelog: String = config?.features?.changelogUrl ?: "https://github.com/openadze/Verre/blob/verre/CHANGELOG.md"
}
