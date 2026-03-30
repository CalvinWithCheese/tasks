package com.example.tasks

import android.app.AlertDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    data class Task(
        val name: String,
        val durationMinutes: Int,
        val location: String,
        var isCompleted: Boolean = false
    )

    private val tasks = mutableListOf<Task>()

    private var selectedTask: Task? = null
    private var timer: CountDownTimer? = null
    private var remainingMillis: Long = 0L
    private var awaitingCompletionDecision = false
    private var selectedLocation: String? = null // null => anywhere

    // Add task section
    private lateinit var taskNameInput: EditText
    private lateinit var taskDurationInput: EditText
    private lateinit var taskLocationInput: EditText
    private lateinit var addTaskButton: Button

    // Location controls
    private lateinit var anywhereButton: Button
    private lateinit var specificLocationButton: Button

    // Spin controls
    private lateinit var button60: Button
    private lateinit var button30: Button
    private lateinit var button10: Button
    private lateinit var customButton: Button

    // Status / timer
    private lateinit var selectedTaskText: TextView
    private lateinit var timerText: TextView
    private lateinit var doneButton: Button
    private lateinit var quitButton: Button
    private lateinit var completedButton: Button
    private lateinit var notCompletedButton: Button

    // Task output
    private lateinit var taskListText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        bindListeners()

        refreshTaskList()
        updateLocationButtonStyles()
        setIdleUiState()
    }

    private fun bindViews() {
        taskNameInput = findViewById(R.id.etTaskName)
        taskDurationInput = findViewById(R.id.etTaskDuration)
        taskLocationInput = findViewById(R.id.etTaskLocation)
        addTaskButton = findViewById(R.id.btnAddTask)

        anywhereButton = findViewById(R.id.btnAnywhere)
        specificLocationButton = findViewById(R.id.btnSpecificLocation)

        button60 = findViewById(R.id.btn60)
        button30 = findViewById(R.id.btn30)
        button10 = findViewById(R.id.btn10)
        customButton = findViewById(R.id.btnCustom)

        selectedTaskText = findViewById(R.id.tvSelectedTask)
        timerText = findViewById(R.id.tvTimer)

        doneButton = findViewById(R.id.btnDone)
        quitButton = findViewById(R.id.btnQuit)
        completedButton = findViewById(R.id.btnCompleted)
        notCompletedButton = findViewById(R.id.btnNotCompleted)

        taskListText = findViewById(R.id.tvTaskList)
    }

    private fun bindListeners() {
        addTaskButton.setOnClickListener { addTask() }

        anywhereButton.setOnClickListener {
            selectedLocation = null
            updateLocationButtonStyles()
        }

        specificLocationButton.setOnClickListener { openLocationPicker() }

        button60.setOnClickListener { quickSpin(60) }
        button30.setOnClickListener { quickSpin(30) }
        button10.setOnClickListener { quickSpin(10) }
        customButton.setOnClickListener { openCustomSpinDialog() }

        doneButton.setOnClickListener { onDonePressed() }
        quitButton.setOnClickListener { onQuitPressed() }
        completedButton.setOnClickListener { onCompletedPressed() }
        notCompletedButton.setOnClickListener { onNotCompletedPressed() }
    }

    private fun addTask() {
        val name = taskNameInput.text.toString().trim()
        val durationStr = taskDurationInput.text.toString().trim()
        val location = taskLocationInput.text.toString().trim()

        if (name.isEmpty() || durationStr.isEmpty() || location.isEmpty()) {
            toast(R.string.msg_missing_fields)
            return
        }

        val duration = durationStr.toIntOrNull()
        if (duration == null || duration <= 0) {
            toast(R.string.msg_invalid_duration)
            return
        }

        tasks.add(Task(name = name, durationMinutes = duration, location = location))
        taskNameInput.text.clear()
        taskDurationInput.text.clear()
        taskLocationInput.text.clear()

        refreshTaskList()
        toast(R.string.msg_task_added)
    }

    private fun openLocationPicker() {
        val uniqueLocations = tasks
            .map { it.location.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        if (uniqueLocations.isEmpty()) {
            toast(R.string.msg_no_locations)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_choose_location)
            .setItems(uniqueLocations.toTypedArray()) { dialog, which ->
                selectedLocation = uniqueLocations[which]
                updateLocationButtonStyles()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.label_cancel, null)
            .show()
    }

    private fun openCustomSpinDialog() {
        if (awaitingCompletionDecision) {
            toast(R.string.msg_choose_completion_first)
            return
        }

        if (selectedTask != null && remainingMillis > 0L) {
            toast(R.string.msg_timer_running)
            return
        }

        val durationInput = EditText(this).apply {
            hint = getString(R.string.hint_duration_required)
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val locationInput = EditText(this).apply {
            hint = getString(R.string.hint_location_optional)
            setText(selectedLocation ?: "")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_custom_filters)
            .setView(
                android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.dialog_horizontal_padding),
                        resources.getDimensionPixelSize(R.dimen.dialog_vertical_padding),
                        resources.getDimensionPixelSize(R.dimen.dialog_horizontal_padding),
                        resources.getDimensionPixelSize(R.dimen.dialog_vertical_padding)
                    )
                    addView(durationInput)
                    addView(locationInput)
                }
            )
            .setPositiveButton(R.string.label_spin, null)
            .setNegativeButton(R.string.label_back, null)
            .create()

        dialog.setOnShowListener {
            val spinButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            spinButton.setOnClickListener {
                val duration = durationInput.text.toString().trim().toIntOrNull()
                if (duration == null || duration <= 0) {
                    toast(R.string.msg_enter_valid_duration)
                    return@setOnClickListener
                }

                val location = locationInput.text.toString().trim().ifBlank { null }
                if (spinTask(duration, location)) {
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun quickSpin(maxDuration: Int) {
        if (awaitingCompletionDecision) {
            toast(R.string.msg_choose_completion_first)
            return
        }

        if (selectedTask != null && remainingMillis > 0L) {
            toast(R.string.msg_timer_running)
            return
        }

        spinTask(maxDuration, selectedLocation)
    }

    private fun spinTask(maxDuration: Int, locationFilter: String?): Boolean {
        val eligible = tasks.filter { task ->
            !task.isCompleted &&
                    task.durationMinutes <= maxDuration &&
                    (locationFilter.isNullOrBlank() || task.location.equals(locationFilter, ignoreCase = true))
        }

        if (eligible.isEmpty()) {
            toast(R.string.msg_no_eligible_tasks)
            return false
        }

        val picked = eligible[Random.nextInt(eligible.size)]
        selectedTask = picked

        selectedTaskText.text = getString(
            R.string.selected_task_format,
            picked.name,
            picked.durationMinutes,
            picked.location
        )

        startTimer(picked.durationMinutes)
        setTimerRunningUiState()
        return true
    }

    private fun startTimer(durationMinutes: Int) {
        timer?.cancel()

        remainingMillis = durationMinutes * 60_000L
        updateTimerText(remainingMillis)

        timer = object : CountDownTimer(remainingMillis, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                updateTimerText(remainingMillis)
            }

            override fun onFinish() {
                timer = null
                remainingMillis = 0L
                updateTimerText(0L)
                awaitingCompletionDecision = true
                setAwaitingCompletionUiState()
                toast(R.string.msg_timer_finished)
            }
        }.start()
    }

    private fun onDonePressed() {
        val task = selectedTask ?: run {
            toast(R.string.msg_no_active_task)
            return
        }

        timer?.cancel()
        timer = null
        remainingMillis = 0L

        task.isCompleted = true
        selectedTask = null
        awaitingCompletionDecision = false

        selectedTaskText.text = getString(R.string.selected_task_none)
        updateTimerText(0L)
        setIdleUiState()
        refreshTaskList()

        toast(R.string.msg_task_marked_complete)
    }

    private fun onQuitPressed() {
        timer?.cancel()
        timer = null
        remainingMillis = 0L
        selectedTask = null
        awaitingCompletionDecision = false

        selectedTaskText.text = getString(R.string.selected_task_none)
        updateTimerText(0L)
        setIdleUiState()

        toast(R.string.msg_timer_quit)
    }

    private fun onCompletedPressed() {
        val task = selectedTask ?: run {
            toast(R.string.msg_no_task_to_complete)
            return
        }

        task.isCompleted = true
        selectedTask = null
        awaitingCompletionDecision = false

        selectedTaskText.text = getString(R.string.selected_task_none)
        setIdleUiState()
        refreshTaskList()

        toast(R.string.msg_task_marked_completed)
    }

    private fun onNotCompletedPressed() {
        selectedTask = null
        awaitingCompletionDecision = false

        selectedTaskText.text = getString(R.string.selected_task_none)
        setIdleUiState()
        refreshTaskList()

        toast(R.string.msg_task_left_incomplete)
    }

    private fun setIdleUiState() {
        setSpinControlsEnabled(true)
        addTaskButton.isEnabled = true

        doneButton.visibility = View.GONE
        quitButton.visibility = View.GONE
        completedButton.visibility = View.GONE
        notCompletedButton.visibility = View.GONE
    }

    private fun setTimerRunningUiState() {
        setSpinControlsEnabled(false)

        doneButton.visibility = View.VISIBLE
        quitButton.visibility = View.VISIBLE
        completedButton.visibility = View.GONE
        notCompletedButton.visibility = View.GONE
    }

    private fun setAwaitingCompletionUiState() {
        setSpinControlsEnabled(false)

        doneButton.visibility = View.GONE
        quitButton.visibility = View.GONE
        completedButton.visibility = View.VISIBLE
        notCompletedButton.visibility = View.VISIBLE
    }

    private fun setSpinControlsEnabled(enabled: Boolean) {
        anywhereButton.isEnabled = enabled
        specificLocationButton.isEnabled = enabled
        button60.isEnabled = enabled
        button30.isEnabled = enabled
        button10.isEnabled = enabled
        customButton.isEnabled = enabled
    }

    private fun refreshTaskList() {
        if (tasks.isEmpty()) {
            taskListText.text = getString(R.string.no_tasks_yet)
            return
        }

        taskListText.text = tasks.mapIndexed { index, task ->
            val status = if (task.isCompleted) getString(R.string.status_completed) else getString(R.string.status_open)
            getString(
                R.string.task_list_item_format,
                index + 1,
                task.name,
                task.durationMinutes,
                task.location,
                status
            )
        }.joinToString("\n")
    }

    private fun updateTimerText(ms: Long) {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        timerText.text = getString(R.string.timer_format, minutes, seconds)
    }

    private fun updateLocationButtonStyles() {
        val selectedColor = ContextCompat.getColor(this, R.color.location_selected)
        val defaultColor = ContextCompat.getColor(this, R.color.location_default)

        if (selectedLocation == null) {
            anywhereButton.setBackgroundColor(selectedColor)
            specificLocationButton.setBackgroundColor(defaultColor)
            specificLocationButton.text = getString(R.string.specific_location_label)
        } else {
            anywhereButton.setBackgroundColor(defaultColor)
            specificLocationButton.setBackgroundColor(selectedColor)
            specificLocationButton.text = selectedLocation
        }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
