package com.example.meydantestapp.utils

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.meydantestapp.common.UiMessage
import com.google.android.material.snackbar.Snackbar
import android.view.View

/**
 * Extension Functions لتسهيل عرض UiMessage في Activities/Fragments
 */

/**
 * تحويل UiMessage إلى String
 */
fun UiMessage.asString(context: Context): String {
    return when (this) {
        is UiMessage.StringResource -> {
            context.getString(resId)
        }
        is UiMessage.StringResourceWithArgs -> {
            context.getString(resId, *args)
        }
        is UiMessage.RawString -> {
            text
        }
    }
}

/**
 * عرض UiMessage كـ Toast
 */
fun Context.showToast(message: UiMessage, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message.asString(this), duration).show()
}

/**
 * عرض UiMessage كـ Toast (نسخة مباشرة)
 */
fun UiMessage.showAsToast(context: Context, duration: Int = Toast.LENGTH_SHORT) {
    context.showToast(this, duration)
}

/**
 * عرض UiMessage كـ Snackbar
 */
fun View.showSnackbar(
    message: UiMessage,
    duration: Int = Snackbar.LENGTH_SHORT,
    actionText: String? = null,
    action: (() -> Unit)? = null
) {
    val snackbar = Snackbar.make(this, message.asString(context), duration)
    
    if (actionText != null && action != null) {
        snackbar.setAction(actionText) { action() }
    }
    
    snackbar.show()
}

/**
 * عرض UiMessage كـ AlertDialog
 */
fun Context.showAlertDialog(
    message: UiMessage,
    title: UiMessage? = null,
    positiveButton: String = "موافق",
    positiveAction: (() -> Unit)? = null,
    negativeButton: String? = null,
    negativeAction: (() -> Unit)? = null
) {
    val builder = AlertDialog.Builder(this)
        .setMessage(message.asString(this))
        .setPositiveButton(positiveButton) { dialog, _ ->
            positiveAction?.invoke()
            dialog.dismiss()
        }
    
    if (title != null) {
        builder.setTitle(title.asString(this))
    }
    
    if (negativeButton != null) {
        builder.setNegativeButton(negativeButton) { dialog, _ ->
            negativeAction?.invoke()
            dialog.dismiss()
        }
    }
    
    builder.create().show()
}

/**
 * عرض رسالة خطأ كـ AlertDialog
 */
fun Context.showErrorDialog(
    message: UiMessage,
    onDismiss: (() -> Unit)? = null
) {
    AlertDialog.Builder(this)
        .setTitle("خطأ")
        .setMessage(message.asString(this))
        .setPositiveButton("موافق") { dialog, _ ->
            onDismiss?.invoke()
            dialog.dismiss()
        }
        .create()
        .show()
}

/**
 * عرض رسالة نجاح كـ AlertDialog
 */
fun Context.showSuccessDialog(
    message: UiMessage,
    onDismiss: (() -> Unit)? = null
) {
    AlertDialog.Builder(this)
        .setTitle("نجح")
        .setMessage(message.asString(this))
        .setPositiveButton("موافق") { dialog, _ ->
            onDismiss?.invoke()
            dialog.dismiss()
        }
        .create()
        .show()
}

/**
 * عرض حوار تأكيد
 */
fun Context.showConfirmDialog(
    message: UiMessage,
    title: UiMessage? = null,
    positiveButton: String = "نعم",
    negativeButton: String = "لا",
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val builder = AlertDialog.Builder(this)
        .setMessage(message.asString(this))
        .setPositiveButton(positiveButton) { dialog, _ ->
            onConfirm()
            dialog.dismiss()
        }
        .setNegativeButton(negativeButton) { dialog, _ ->
            onCancel?.invoke()
            dialog.dismiss()
        }
    
    if (title != null) {
        builder.setTitle(title.asString(this))
    }
    
    builder.create().show()
}
