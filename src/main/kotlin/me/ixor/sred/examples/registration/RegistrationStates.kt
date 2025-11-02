package me.ixor.sred.examples.registration

/**
 * 用户注册状态ID常量
 */
object RegistrationStates {
    const val INITIATED = "registration_initiated"
    const val VALIDATING = "validating"
    const val SENDING_EMAIL = "sending_email"
    const val WAITING_VERIFICATION = "waiting_verification"
    const val VERIFYING_CODE = "verifying_code"
    const val ACTIVATING = "activating"
    const val SUCCESS = "registration_success"
    const val FAILED = "registration_failed"
}

