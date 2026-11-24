package com.example.unimate.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val university: String = "",
    val department: String = "",
    val semester: String = "",
    val phoneNumber: String = "",
    val profileImage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class ProfileSetupRequest(
    val fullName: String,
    val email: String? = null,
    val university: String,
    val department: String,
    val semester: String,
    val phoneNumber: String? = null,
    val profileImage: String? = null,
)

data class ProfileUpdateRequest(
    val fullName: String,
    val university: String,
    val department: String,
    val semester: String,
    val phoneNumber: String? = null,
    val profileImage: String? = null,
)

object UserProfileMapper {
    fun toCreatePayload(
        uid: String,
        email: String,
        request: ProfileSetupRequest,
    ): Map<String, Any> {
        val resolvedEmail = email.trim().ifEmpty { request.email?.trim().orEmpty() }
        val payload = mutableMapOf<String, Any>(
            "uid" to uid,
            "fullName" to request.fullName.trim(),
            "email" to resolvedEmail,
            "university" to request.university.trim(),
            "department" to request.department.trim(),
            "semester" to request.semester.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val cleanPhone = request.phoneNumber?.trim().orEmpty()
        if (cleanPhone.isNotEmpty()) {
            payload["phoneNumber"] = cleanPhone
        }
        val cleanImage = request.profileImage?.trim().orEmpty()
        if (cleanImage.isNotEmpty()) {
            payload["profileImage"] = cleanImage
        }
        return payload
    }

    fun toUpdatePayload(
        request: ProfileUpdateRequest,
    ): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "fullName" to request.fullName.trim(),
            "university" to request.university.trim(),
            "department" to request.department.trim(),
            "semester" to request.semester.trim(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (request.phoneNumber != null) {
            val cleanPhone = request.phoneNumber.trim()
            if (cleanPhone.isNotEmpty()) {
                payload["phoneNumber"] = cleanPhone
            }
        }
        val cleanImage = request.profileImage?.trim().orEmpty()
        if (cleanImage.isNotEmpty()) {
            payload["profileImage"] = cleanImage
        } else {
            payload["profileImage"] = FieldValue.delete()
        }
        return payload
    }

    fun toPhoneUpdatePayload(phoneNumber: String): Map<String, Any> = mapOf(
        "phoneNumber" to phoneNumber.trim(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}
