package com.fuwaki.djifly.platform.attachment

import java.io.File

/** Current decoded FPV evidence only (REQ-DJIFLY-001; TASK-PLATFORM-002). */
sealed interface FpvPhotoResult {
    data class Ok(val file: File, val fileName: String) : FpvPhotoResult
    data class Failed(val reason: String) : FpvPhotoResult
}
