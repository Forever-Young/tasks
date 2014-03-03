package org.tasks.widget;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Paint;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.service.Autowired;
import com.todoroo.andlib.service.DependencyInjectionService;
import com.todoroo.andlib.utility.Preferences;
import com.todoroo.astrid.actfm.TagViewFragment;
import com.todoroo.astrid.activity.TaskEditFragment;
import com.todoroo.astrid.activity.TaskListActivity;
import com.todoroo.astrid.adapter.TaskAdapter;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.api.FilterWithCustomIntent;
import com.todoroo.astrid.core.SortHelper;
import com.todoroo.astrid.dao.Database;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.service.TaskService;
import com.todoroo.astrid.subtasks.SubtasksHelper;
import com.todoroo.astrid.utility.AstridPreferences;
import com.todoroo.astrid.utility.Constants;
import com.todoroo.astrid.widget.TasksWidget;
import com.todoroo.astrid.widget.WidgetConfigActivity;

import org.tasks.R;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class ScrollableViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    @Autowired
    Database database;

    @Autowired
    TaskService taskService;

    private final Context context;
    private final Filter filter;
    private final int widgetId;

    private TodorooCursor<Task> cursor;

    public ScrollableViewsFactory(Context context, Filter filter, int widgetId) {
        this.context = context;
        this.filter = filter;
        this.widgetId = widgetId;
    }

    @Override
    public void onCreate() {
        DependencyInjectionService.getInstance().inject(this);

        database.openForReading();
        cursor = getCursor();
    }

    @Override
    public void onDataSetChanged() {
        cursor = getCursor();
    }

    @Override
    public void onDestroy() {
        cursor.close();
    }

    @Override
    public int getCount() {
        return cursor.getCount();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        return buildUpdate(position);
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return getTask(position).getId();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private int getCheckbox(Task task) {
        boolean completed = task.isCompleted();

        int value = task.getImportance();
        if (value >= TaskAdapter.IMPORTANCE_RESOURCES.length) {
            value = TaskAdapter.IMPORTANCE_RESOURCES.length - 1;
        }
        int[] boxes;
        if (!TextUtils.isEmpty(task.getRecurrence())) {
            boxes = completed ? TaskAdapter.IMPORTANCE_REPEAT_RESOURCES_CHECKED : TaskAdapter.IMPORTANCE_REPEAT_RESOURCES;
        } else {
            boxes = completed ? TaskAdapter.IMPORTANCE_RESOURCES_CHECKED : TaskAdapter.IMPORTANCE_RESOURCES;
        }
        return boxes[value];
    }

    public RemoteViews buildUpdate(int position) {
        try {
            Task task = getTask(position);

            String textContent;
            Resources r = context.getResources();
            int textColor = Preferences.getInt(R.string.p_widget_text_color, context.getResources().getColor(R.color.widget_text_color_light));

            textContent = task.getTitle();

            RemoteViews row = new RemoteViews(Constants.PACKAGE, R.layout.widget_row);

            if (task.isCompleted()) {
                textColor = r.getColor(R.color.task_list_done);
                row.setInt(R.id.text, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
            } else {
                row.setInt(R.id.text, "setPaintFlags", Paint.ANTI_ALIAS_FLAG);
                if (task.hasDueDate() && task.isOverdue()) {
                    textColor = r.getColor(R.color.task_list_overdue);
                }
            }

            row.setTextViewText(R.id.text, textContent);
            row.setTextColor(R.id.text, textColor);
            row.setImageViewResource(R.id.completeBox, getCheckbox(task));

            Intent editIntent = new Intent();
            editIntent.setAction(TasksWidget.EDIT_TASK);
            editIntent.putExtra(TaskEditFragment.TOKEN_ID, task.getId());
            editIntent.putExtra(TaskListActivity.OPEN_TASK, task.getId());
            row.setOnClickFillInIntent(R.id.text, editIntent);

            Intent completeIntent = new Intent();
            completeIntent.setAction(TasksWidget.COMPLETE_TASK);
            completeIntent.putExtra(TaskEditFragment.TOKEN_ID, task.getId());
            row.setOnClickFillInIntent(R.id.completeBox, completeIntent);

            return row;
        } catch (Exception e) {
            // can happen if database is not ready
            Log.e("WIDGET-UPDATE", "Error updating widget", e);
        }

        return null;
    }

    private TodorooCursor<Task> getCursor() {
        String query = getQuery(context);
        return taskService.fetchFiltered(query, null, Task.ID, Task.TITLE, Task.DUE_DATE, Task.COMPLETION_DATE, Task.IMPORTANCE, Task.RECURRENCE);
    }

    private Task getTask(int position) {
        cursor.moveToPosition(position);
        return new Task(cursor);
    }

    private String getQuery(Context context) {
        if (SubtasksHelper.isTagFilter(filter)) {
            ((FilterWithCustomIntent) filter).customTaskList = new ComponentName(context, TagViewFragment.class); // In case legacy widget was created with subtasks fragment
        }

        SharedPreferences publicPrefs = AstridPreferences.getPublicPrefs(context);
        int flags = publicPrefs.getInt(SortHelper.PREF_SORT_FLAGS, 0);
        flags |= SortHelper.FLAG_SHOW_RECENTLY_COMPLETED;
        int sort = publicPrefs.getInt(SortHelper.PREF_SORT_SORT, 0);
        if(sort == 0) {
            sort = SortHelper.SORT_WIDGET;
        }

        String query = SortHelper.adjustQueryForFlagsAndSort(
                filter.getSqlQuery(), flags, sort).replaceAll("LIMIT \\d+", "");

        String tagName = Preferences.getStringValue(WidgetConfigActivity.PREF_TITLE + widgetId);

        return SubtasksHelper.applySubtasksToWidgetFilter(filter, query, tagName, 0);
    }
}
