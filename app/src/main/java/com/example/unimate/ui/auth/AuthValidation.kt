package com.example.unimate.ui.auth

import androidx.annotation.StringRes
import com.example.unimate.R

object AuthValidation {

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
    )

    private val E164_PHONE_REGEX = Regex("^\\+[1-9]\\d{6,14}$")
    private val OTP_REGEX = Regex("^\\d{6}$")

    @StringRes
    fun validateFullName(name: String): Int? {
        val len = name.trim().length
        return if (len !in 2..100) {
            R.string.auth_error_full_name_required
        } else {
            null
        }
    }

    @StringRes
    fun validateEmail(email: String): Int? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> R.string.auth_error_email_required
            trimmed.length > 254 || !EMAIL_REGEX.matches(trimmed) -> R.string.auth_error_email_invalid
            else -> null
        }
    }

    @StringRes
    fun validatePhoneNumber(phone: String): Int? {
        val normalized = phone.trim().replace(" ", "").replace("-", "")
        return if (!E164_PHONE_REGEX.matches(normalized) || normalized.length > 30) {
            R.string.auth_error_phone_invalid
        } else {
            null
        }
    }

    @StringRes
    fun validateOtpCode(code: String): Int? {
        return if (!OTP_REGEX.matches(code.trim())) {
            R.string.auth_error_otp_invalid
        } else {
            null
        }
    }

    fun normalizePhoneNumber(phone: String): String =
        phone.trim().replace(" ", "").replace("-", "")

    @StringRes
    fun validateUniversity(university: String): Int? {
        val len = university.trim().length
        return if (len !in 2..150) {
            R.string.auth_error_university_required
        } else {
            null
        }
    }

    @StringRes
    fun validateDepartment(department: String): Int? {
        val len = department.trim().length
        return if (len !in 2..120) {
            R.string.auth_error_department_required
        } else {
            null
        }
    }

    @StringRes
    fun validateSemester(semester: String): Int? {
        val len = semester.trim().length
        return if (len !in 1..60) {
            R.string.auth_error_semester_required
        } else {
            null
        }
    }

    @StringRes
    fun validateProfileImageUrl(url: String): Int? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val isHttp = trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        return if (!isHttp || trimmed.length > 500) {
            R.string.profile_error_image_url_invalid
        } else {
            null
        }
    }

    val semesterOptions: List<String> = listOf(
        "1st Semester",
        "2nd Semester",
        "3rd Semester",
        "4th Semester",
        "5th Semester",
        "6th Semester",
        "7th Semester",
        "8th Semester",
        "Graduate / Master's",
        "PhD / Research",
    )
}
