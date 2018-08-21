package mil.nga.giat.mage.data

import android.os.AsyncTask
import android.os.Looper
import android.support.annotation.MainThread
import android.support.annotation.WorkerThread
import java.util.*
import java.util.concurrent.Executor

@MainThread
class UniqueAsyncTaskManager<Key, Progress, Result>(private val listener: TaskListener<Key, Progress, Result>, val executor: Executor = AsyncTask.THREAD_POOL_EXECUTOR) {

    private val tasks = HashMap<Key, TaskPair<Key, Progress, Result>>()
    private var disposed: Boolean = false

    fun execute(key: Key, task: Task<Progress, Result>) {
        if (Looper.getMainLooper() !== Looper.myLooper()) {
            throw Error("task was submitted from non-main thread: $key")
        }
        if (disposed) {
            throw IllegalStateException("$this is disposed but attempted to execute task for key $key")
        }
        val managedTask = UniqueAsyncTask(this, key, task)
        var taskPair = tasks.remove(key)
        if (taskPair == null) {
            taskPair = TaskPair(managedTask)
            tasks[key] = taskPair
            taskPair.current.executeOnExecutor(executor)
            return
        }
        val (current, pending) = taskPair
        tasks[key] = TaskPair(current, managedTask)
        current.cancel(false)
        pending?.cancel(false)
    }

    fun execute(key: Key, task: (support: TaskSupport<Progress>) -> Result) {
        execute(key, object : Task<Progress, Result> {
            override fun run(support: TaskSupport<Progress>): Result {
                return task.invoke(support)
            }
        })
    }

    fun dispose() {
        disposed = true
        val keys = tasks.keys.toList()
        for (key in keys) {
            val taskPair = tasks.remove(key)!!
            taskPair.current.cancel(false)
            if (taskPair.pending != null) {
                taskPair.pending.cancel(false)
            }
        }
    }

    private fun onTaskFinished(finished: UniqueAsyncTask<Key, Progress, Result>, result: Result?) {
        if (disposed) {
            return
        }
        val key = finished.key
        val (current, pending) = tasks[key]!!
        if (finished === current) {
            if (finished.isCancelled) {
                listener.taskCancelled(key, finished.delegate)
            }
            else {
                listener.taskFinished(key, finished.delegate, result)
            }
            if (pending == null) {
                tasks.remove(key)
            }
            else {
                tasks[key] = TaskPair(pending, null)
                pending.executeOnExecutor(executor)
            }
        }
        else if (finished.isCancelled) {
            listener.taskPreempted(finished.key, finished.delegate)
        }
        else {
            throw IllegalStateException("finished task was not cancelled but matched pending task for key ${finished.key}")
        }
    }

    fun isRunningTaskForKey(key: Key): Boolean {
        return currentTaskForKey(key) != null
    }

    fun currentTaskForKey(key: Key): Task<Progress, Result>? {
        return tasks[key]?.current?.delegate
    }

    fun pendingTaskForKey(key: Key): Task<Progress, Result>? {
        return tasks[key]?.pending?.delegate
    }

    interface TaskSupport<Progress> {
        fun isCancelled(): Boolean
        @WorkerThread
        fun reportProgressToMainThread(progress: Progress)
    }

    @FunctionalInterface
    @WorkerThread
    interface Task<Progress, Result> {
        fun run(support:TaskSupport<Progress>): Result?
    }

    @MainThread
    interface TaskListener<Key, Progress, Result> {

        /**
         * The task finished normally.
         */
        fun taskFinished(key: Key, task: Task<Progress, Result>, result: Result?)

        /**
         * The task was cancelled after it began executing.
         */
        @JvmDefault
        fun taskCancelled(key: Key, task: Task<Progress, Result>) {}

        /**
         * The task was cancelled before it began executing.
         */
        @JvmDefault
        fun taskPreempted(key: Key, task: Task<Progress, Result>) {}

        @JvmDefault
        fun taskProgress(key: Key, task: Task<Progress, Result>, progress: Progress) {}
    }

    private data class TaskPair<K, Progress, Result>(val current: UniqueAsyncTask<K, Progress, Result>, val pending: UniqueAsyncTask<K, Progress, Result>? = null)

    private class UniqueAsyncTask<Key, Progress, Result>(val manager: UniqueAsyncTaskManager<Key, Progress, Result>, val key: Key, val delegate: Task<Progress, Result>) : AsyncTask<Void, Progress, Result>(), TaskSupport<Progress> {

        override fun doInBackground(vararg params: Void?): Result? {
            if (isCancelled) {
                return null
            }
            return try {
                delegate.run(this)
            }
            catch (e: Exception) {
                return null
            }
        }

        override fun reportProgressToMainThread(progress: Progress) {
            super.publishProgress(progress)
        }

        override fun onProgressUpdate(vararg progress: Progress) {
            manager.listener.taskProgress(key, delegate, progress[0])
        }

        override fun onPostExecute(result: Result?) {
            manager.onTaskFinished(this, result)
        }

        override fun onCancelled() {
            onPostExecute(null)
        }

        override fun toString(): String {
            return "${javaClass.simpleName}[$key]"
        }
    }
}