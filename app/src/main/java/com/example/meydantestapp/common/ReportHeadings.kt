package com.example.meydantestapp.common

import android.content.Context
import androidx.annotation.StringRes
import com.example.meydantestapp.R

/**
 * Centralized access to shared report section headings so the UI and PDF stay in sync.
 */
object ReportHeadings {

    @StringRes
    private val activitiesRes: Int = R.string.report_section_activities

    @StringRes
    private val equipmentRes: Int = R.string.report_section_equipment

    @StringRes
    private val obstaclesRes: Int = R.string.report_section_obstacles

    @StringRes
    private val projectLocationRes: Int = R.string.report_section_project_location

    @StringRes
    private val infoRes: Int = R.string.report_section_info

    fun activities(context: Context): String = context.getString(activitiesRes)

    fun equipment(context: Context): String = context.getString(equipmentRes)

    fun obstacles(context: Context): String = context.getString(obstaclesRes)

    fun projectLocation(context: Context): String = context.getString(projectLocationRes)

    fun info(context: Context): String = context.getString(infoRes)
}
