package com.phegondev.usersmanagementsystem.entity.timesheet;


import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "task_entry")
public class TaskEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Task reference details
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "task_name")
    private String taskName;

    // Many tasks belong to one project
    @ManyToOne
    @JoinColumn(name = "project_entry_id") // FK in task_entry table
    private ProjectEntry projectEntry;

    // One task has multiple daily entries
    @OneToMany(mappedBy = "taskEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DayEntry> days = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getTaskId() {
		return taskId;
	}

	public void setTaskId(Long taskId) {
		this.taskId = taskId;
	}

	public String getTaskName() {
		return taskName;
	}

	public void setTaskName(String taskName) {
		this.taskName = taskName;
	}

	public ProjectEntry getProjectEntry() {
		return projectEntry;
	}

	public void setProjectEntry(ProjectEntry projectEntry) {
		this.projectEntry = projectEntry;
	}

	public List<DayEntry> getDays() {
		return days;
	}

	public void setDays(List<DayEntry> days) {
		this.days = days;
	}
    
    
}

