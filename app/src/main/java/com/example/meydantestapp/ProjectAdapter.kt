package com.example.meydantestapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.data.model.Project


class ProjectAdapter(
    private val projects: List<Project>,
    private val onEditClick: (Project) -> Unit,
    private val onViewClick: (Project) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val projectNameText: TextView = itemView.findViewById(R.id.projectName)
        val projectLocationText: TextView = itemView.findViewById(R.id.projectLocation)
        val editProjectButton: Button = itemView.findViewById(R.id.editButton)
        val viewProjectButton: Button = itemView.findViewById(R.id.viewButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = projects[position]
        holder.projectNameText.text = project.projectName
        holder.projectLocationText.text = project.addressText ?: ""
        holder.editProjectButton.setOnClickListener { onEditClick(project) }
        holder.viewProjectButton.setOnClickListener { onViewClick(project) }
    }

    override fun getItemCount(): Int = projects.size
}

