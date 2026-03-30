package com.example.tasks // <-- change to your actual package name

import android.app.AlertDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    private var selectedLocation: String? = null // null => Anywhere

    private lateinit var rootLayout: LinearLayout

    // Add task controls
    private lateinit var taskNameInput: EditText
    private lateinit var taskDurationInput: EditText
    private lateinit var taskLocationInput: EditText
    private lateinit var addTaskButton: Button

    // Quick location controls
    private lateinit var anywhereButton: Button
    private lateinit var specificLocationButton: Button

    // Quick spin controls
    private lateinit var button60: Button
    private lateinit var button30: Button
    private lateinit var button10: Button
    private lateinit var customButton: Button

    // Status / timer controls
    private lateinit var selectedTaskText: TextView
    private lateinit var timerText: TextView
    private lateinit var doneButton: Button
    private lateinit var quitButton: Button
    private lateinit var completedButton: Button
    private lateinit var notCompletedButton: Button

    // Task list
    private lateinit var taskListText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 32)
        }

        val scroll = ScrollView(this).apply {
            addView(rootLayout)
        }

        setContentView(scroll)

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        buildUi()
        refreshTaskList()
        updateLocationButtonStyles()
        setIdleUiState()
    }

    private fun buildUi() {
        addHeader("Add Task")
        taskNameInput = addEditText("Task name")
        taskDurationInput = addEditText("Duration (minutes)", InputType.TYPE_CLASS_NUMBER)
        taskLocationInput = addEditText("Location")
        addTaskButton = addButton("Add Task") { addTask() }

        addSpacer()

        addHeader("Quick Spin")

        // Top row: Anywhere | [Specific Location]
        val locationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
        }

        anywhereButton = Button(this).apply {
            text = "Anywhere"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 12
            }
            setOnClickListener {
                selectedLocation = null
                updateLocationButtonStyles()
            }
        }

        specificLocationButton = Button(this).apply {
            text = "[Specific Location]"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12
            }
            setOnClickListener { openLocationPicker() }
        }

        locationRow.addView(anywhereButton)
        locationRow.addView(specificLocationButton)
        rootLayout.addView(locationRow)

        addSpacer(16)

        // 2x2 square button grid
        val grid = GridLayout(this).apply {
            rowCount = 2
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        button60 = makeSquareGridButton("60 min") { quickSpin(60) }
        button30 = makeSquareGridButton("30 min") { quickSpin(30) }
        button10 = makeSquareGridButton("10 min") { quickSpin(10) }
        customButton = makeSquareGridButton("Custom") { openCustomSpinDialog() }

        grid.addView(button60)
        grid.addView(button30)
        grid.addView(button10)
        grid.addView(customButton)

        rootLayout.addView(grid)

        addSpacer()

        selectedTaskText = addText("Selected task: none")
        timerText = addText("Timer: --:--")

        doneButton = addButton("Done") { onDonePressed() }
        quitButton = addButton("Quit") { onQuitPressed() }
        completedButton = addButton("Completed") { onCompletedPressed() }
        notCompletedButton = addButton("Not Completed") { onNotCompletedPressed() }

        addSpacer()

        addHeader("Tasks")
        taskListText = addText("")
    }

    private fun makeSquareGridButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            minHeight = 220
            minimumHeight = 220
            setOnClickListener { onClick() }
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            }
        }
    }

    private fun addTask() {
        val name = taskNameInput.text.toString().trim()
        val durationStr = taskDurationInput.text.toString().trim()
        val location = taskLocationInput.text.toString().trim()

        if (name.isEmpty() || durationStr.isEmpty() || location.isEmpty()) {
            toast("Please enter name, duration, and location.")
            return
        }

        val duration = durationStr.toIntOrNull()
        if (duration == null || duration <= 0) {
            toast("Duration must be a positive number.")
            return
        }

        tasks.add(Task(name = name, durationMinutes = duration, location = location))
        taskNameInput.text.clear()
        taskDurationInput.text.clear()
        taskLocationInput.text.clear()

        refreshTaskList()
        toast("Task added.")
    }

    private fun openLocationPicker() {
        val uniqueLocations = tasks
            .map { it.location.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        if (uniqueLocations.isEmpty()) {
            toast("No task locations available yet.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Choose Location")
            .setItems(uniqueLocations.toTypedArray()) { dialog, which ->
                selectedLocation = uniqueLocations[which]
                updateLocationButtonStyles()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openCustomSpinDialog() {
        if (awaitingCompletionDecision) {
            toast("Please choose Completed / Not Completed first.")
            return
        }

        if (selectedTask != null && remainingMillis > 0L) {
            toast("Timer already running. Press Done or Quit first.")
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 8)
        }

        val durationInput = EditText(this).apply {
            hint = "Duration (required, minutes)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val locationInput = EditText(this).apply {
            hint = "Location (optional)"
            setText(selectedLocation ?: "")
        }

        container.addView(durationInput)
        container.addView(locationInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Custom Filters")
            .setView(container)
            .setPositiveButton("Spin", null)
            .setNegativeButton("Back", null)
            .create()

        dialog.setOnShowListener {
            val spinBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            spinBtn.setOnClickListener {
                val duration = durationInput.text.toString().trim().toIntOrNull()
                if (duration == null || duration <= 0) {
                    toast("Enter a valid duration.")
                    return@setOnClickListener
                }

                val location = locationInput.text.toString().trim().ifBlank { null }
                val spun = spinTask(duration, location)
                if (spun) {
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun quickSpin(maxDuration: Int) {
        if (awaitingCompletionDecision) {
            toast("Please choose Completed / Not Completed first.")
            return
        }

        if (selectedTask != null && remainingMillis > 0L) {
            toast("Timer already running. Press Done or Quit first.")
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
            toast("No eligible tasks found.")
            return false
        }

        val picked = eligible[Random.nextInt(eligible.size)]
        selectedTask = picked

        selectedTaskText.text = "Selected task: ${picked.name} (${picked.durationMinutes}m @ ${picked.location})"
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
                toast("Timer ended. Tap Completed or Not Completed.")
            }
        }.start()
    }

    private fun onDonePressed() {
        val task = selectedTask ?: run {
            toast("No active task.")
            return
        }

        timer?.cancel()
        timer = null
        remainingMillis = 0L

        task.isCompleted = true
        selectedTask = null
        awaitingCompletionDecision = false

        selectedTaskText.text = "Selected task: none"
        updateTimerText(0L)
        setIdleUiState()
        refreshTaskList()

        toast("Task marked complete.")
    }

    private fun onQuitPressed() {
        timer?.cancel()
        timer = null
        remainingMillis = 0L
        selectedTask = null
        awaitingCompletionDecision = false

        selectedTaskText.text = "Selected task: none"
        updateTimerText(0L)
        setIdleUiState()

        toast("Timer quit. Task not completed.")
    }

    private fun onCompletedPressed() {
        val task = selectedTask ?: run {
            toast("No task to complete.")
            return
        }

        task.isCompleted = true
        selectedTask = null
        awaitingCompletionDecision = false

        selectedTaskText.text = "Selected task: none"
        setIdleUiState()
        refreshTaskList()

        toast("Task marked completed.")
    }

    private fun onNotCompletedPressed() {
        selectedTask = null
        awaitingCompletionDecision = false

        selectedTaskText.text = "Selected task: none"
        setIdleUiState()
        refreshTaskList()

        toast("Task left incomplete.")
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
            taskListText.text = "No tasks yet."
            return
        }

        taskListText.text = tasks.mapIndexed { index, task ->
            val status = if (task.isCompleted) "COMPLETED" else "OPEN"
            "${index + 1}. ${task.name} - ${task.durationMinutes}m @ ${task.location} [$status]"
        }.joinToString("\n")
    }

    private fun updateTimerText(ms: Long) {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        timerText.text = String.format("Timer: %02d:%02d", minutes, seconds)
    }

    private fun updateLocationButtonStyles() {
        val selectedColor = ContextCompat.getColor(this, android.R.color.holo_blue_light)
        val defaultColor = ContextCompat.getColor(this, android.R.color.darker_gray)

        if (selectedLocation == null) {
            anywhereButton.setBackgroundColor(selectedColor)
            specificLocationButton.setBackgroundColor(defaultColor)
            specificLocationButton.text = "[Specific Location]"
        } else {
            anywhereButton.setBackgroundColor(defaultColor)
            specificLocationButton.setBackgroundColor(selectedColor)
            specificLocationButton.text = selectedLocation
        }
    }

    private fun addHeader(text: String): TextView {
        return TextView(this).also {
            it.text = text
            it.textSize = 20f
            rootLayout.addView(it)
        }
    }

    private fun addText(text: String): TextView {
        return TextView(this).also {
            it.text = text
            it.textSize = 16f
            rootLayout.addView(it)
        }
    }

    private fun addEditText(hint: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText {
        return EditText(this).also {
            it.hint = hint
            it.inputType = inputType
            rootLayout.addView(it)
        }
    }

    private fun addButton(text: String, onClick: () -> Unit): Button {
        return Button(this).also {
            it.text = text
            it.setOnClickListener { onClick() }
            rootLayout.addView(it)
        }
    }

    private fun addSpacer(heightPx: Int = 24) {
        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            heightPx
        )
        rootLayout.addView(spacer)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
