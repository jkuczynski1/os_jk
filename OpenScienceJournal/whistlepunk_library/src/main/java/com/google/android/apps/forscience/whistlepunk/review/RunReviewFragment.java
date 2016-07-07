/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.review;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AddNoteDialog;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.AudioSettingsDialog;
import com.google.android.apps.forscience.whistlepunk.CurrentTimeClock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.EditNoteDialog;
import com.google.android.apps.forscience.whistlepunk.ElapsedTimeFormatter;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisView;
import com.google.android.apps.forscience.whistlepunk.LocalSensorOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartView;
import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphOptionsController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.PreviewNoteDialog;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecordFragment;
import com.google.android.apps.forscience.whistlepunk.RunReviewOverlay;
import com.google.android.apps.forscience.whistlepunk.ScalarDataLoader;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.StatsList;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.audiogen.SimpleJsynAudioGenerator;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.PictureLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.android.apps.forscience.whistlepunk.metadata.TextLabel;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;
import com.google.android.apps.forscience.whistlepunk.review.EditTimeDialog.EditTimeDialogListener;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RunReviewFragment extends Fragment implements AddNoteDialog.AddNoteDialogListener,
        EditNoteDialog.EditNoteDialogListener, EditTimeDialogListener,
        DeleteRunDialog.DeleteRunDialogListener, AudioSettingsDialog.AudioSettingsDialogListener,
        ChartController.ChartLoadingStatus {
    public static final String ARG_START_LABEL_ID = "start_label_id";
    public static final String ARG_SENSOR_INDEX = "sensor_tag_index";
    public static final String ARG_CREATE_TASK = "create_task";
    private static final String TAG = "RunReviewFragment";

    private static final String KEY_SELECTED_SENSOR_INDEX = "selected_sensor_index";
    private static final String KEY_TIMESTAMP_EDIT_UI_VISIBLE = "timestamp_edit_visible";
    private static final String KEY_EXTERNAL_AXIS_MINIMUM = "external_axis_min";
    private static final String KEY_EXTERNAL_AXIS_MAXIMUM = "external_axis_max";
    private static final String KEY_RUN_REVIEW_OVERLAY_TIMESTAMP = "run_review_overlay_time";

    private int mLoadingStatus = GRAPH_LOAD_STATUS_IDLE;

    public static final double MILLIS_IN_A_SECOND = 1000.0;

    public static final int LABEL_TYPE_UNDECIDED = -1;
    public static final int LABEL_TYPE_TEXT = 0;
    public static final int LABEL_TYPE_PICTURE = 1;

    // TODO: For sensors with more than 100 datapoints in 1 second, these constants may need to be
    // adjusted!
    private static final int DATAPOINTS_PER_AUDIO_PLAYBACK_LOAD = 200;
    private static final long DURATION_MS_PER_AUDIO_PLAYBACK_LOAD = 2000;
    private static final int PLAYBACK_STATUS_NOT_PLAYING = 0;
    private static final int PLAYBACK_STATUS_LOADING = 1;
    private static final int PLAYBACK_STATUS_PLAYING = 2;
    private int mPlaybackStatus = PLAYBACK_STATUS_NOT_PLAYING;
    private ImageButton mRunReviewPlaybackButton;
    private SimpleJsynAudioGenerator mAudioGenerator;
    private Handler mHandler;
    private Runnable mPlaybackRunnable;
    private boolean mWasPlayingBeforeTouch = false;

    private String mStartLabelId;
    private int mSelectedSensorIndex = 0;
    private GraphOptionsController mGraphOptionsController;
    private ScalarDisplayOptions mScalarDisplayOptions;
    private ChartController mChartController;
    private ExternalAxisController mExternalAxis;
    private RunReviewOverlay mRunReviewOverlay;
    private PinnedNoteAdapter mPinnedNoteAdapter;
    private ExperimentRun mExperimentRun;
    private Experiment mExperiment;
    private ActionMode mActionMode;
    private ProgressBar mExportProgress;
    private RunReviewExporter mRunReviewExporter;
    private RunStats mCurrentSensorStats;
    private boolean mShowStatsOverlay = false;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param startLabelId the startLabelId that joins the labels identifying this run
     * @param sensorIndex the initial sensor to select in run review
     * @param createTask if {@code true}, will create tasks when navigating up
     * @return A new instance of fragment RunReviewFragment.
     */
    public static RunReviewFragment newInstance(String startLabelId, int sensorIndex,
            boolean createTask) {
        RunReviewFragment fragment = new RunReviewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_START_LABEL_ID, startLabelId);
        args.putInt(ARG_SENSOR_INDEX, sensorIndex);
        args.putBoolean(ARG_CREATE_TASK, createTask);
        fragment.setArguments(args);
        return fragment;
    }

    public RunReviewFragment() {
        // Required empty public constructor
    }

    @Override
    public void onResume() {
        super.onResume();
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_RUN_REVIEW);
    }

    @Override
    public void onDestroy() {
        stopPlayback();
        mGraphOptionsController = null;
        if (mExternalAxis != null) {
            mExternalAxis.destroy();
        }
        if (mPinnedNoteAdapter != null) {
            mPinnedNoteAdapter.onDestroy();
        }
        if (mRunReviewOverlay != null) {
            mRunReviewOverlay.onDestroy();
        }
        if (mChartController != null) {
            mChartController.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        stopPlayback();
        super.onPause();
    }

    @Override
    public void onStop() {
        if (mRunReviewExporter.isExporting()) {
            mRunReviewExporter.stop();
        }
        super.onStop();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mStartLabelId = getArguments().getString(ARG_START_LABEL_ID);
            mSelectedSensorIndex = getArguments().getInt(ARG_SENSOR_INDEX);
        }
        if (savedInstanceState != null &&
                savedInstanceState.containsKey(KEY_SELECTED_SENSOR_INDEX)) {
            // saved instance state is more recent than args, so it takes precedence.
            mSelectedSensorIndex = savedInstanceState.getInt(KEY_SELECTED_SENSOR_INDEX);
        }
        mRunReviewExporter = new RunReviewExporter(getDataController(),
                new RunReviewExporter.Listener() {
                    @Override
                    public void onExportStarted() {
                        showExportUi();
                    }

                    @Override
                    public void onExportProgress(int progress) {
                        setExportProgress(progress);
                    }

                    @Override
                    public void onExportEnd(Uri uri) {
                        resetExportUi();
                        if (uri != null) {
                            Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                            intent.setType("application/octet-stream");
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            getActivity().startActivity(Intent.createChooser(intent, getString(
                                    R.string.export_run_chooser_title)));
                        }
                    }

                    @Override
                    public void onExportError(Exception e) {
                        resetExportUi();
                        Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                                getString(R.string.export_error), Snackbar.LENGTH_LONG);
                        bar.show();
                    }
                });
        setHasOptionsMenu(true);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // Hide archive and unarchive buttons if the run isn't loaded yet.
        if (mExperimentRun != null) {
            menu.findItem(R.id.action_run_review_archive).setVisible(!mExperimentRun.isArchived());
            menu.findItem(R.id.action_run_review_unarchive).setVisible(mExperimentRun.isArchived());
            menu.findItem(R.id.action_disable_auto_zoom).setVisible(
                    mExperimentRun.getAutoZoomEnabled());
            menu.findItem(R.id.action_enable_auto_zoom).setVisible(
                    !mExperimentRun.getAutoZoomEnabled());
        } else {
            menu.findItem(R.id.action_run_review_archive).setVisible(false);
            menu.findItem(R.id.action_run_review_unarchive).setVisible(false);
            menu.findItem(R.id.action_disable_auto_zoom).setVisible(false);
            menu.findItem(R.id.action_enable_auto_zoom).setVisible(false);
        }
        menu.findItem(R.id.action_export).setEnabled(!mRunReviewExporter.isExporting());

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_run_review, container, false);
        mExportProgress = (ProgressBar) rootView.findViewById(R.id.export_progress);
        AppBarLayout appBarLayout = (AppBarLayout) rootView.findViewById(R.id.app_bar_layout);
        ViewCompat.setTransitionName(appBarLayout, mStartLabelId);
        ExternalAxisView externalAxisView =
                (ExternalAxisView) rootView.findViewById(R.id.external_x_axis);
        mExternalAxis = new ExternalAxisController(externalAxisView,
                new ExternalAxisController.AxisUpdateListener() {
                    @Override
                    public void onAxisUpdated(long xMin, long xMax, boolean isPinnedToNow) {
                        mChartController.setXAxis(xMin, xMax);
                    }
                }, /* IsLive */ false, new CurrentTimeClock());
        mRunReviewOverlay =
                (RunReviewOverlay) rootView.findViewById(R.id.run_review_chart_overlay);
        mRunReviewOverlay.setSeekbarView(
                (GraphExploringSeekBar) rootView.findViewById(R.id.external_axis_seekbar));
        mRunReviewOverlay.setExternalAxisController(mExternalAxis);
        mRunReviewOverlay.setOnSeekbarTouchListener(
                new RunReviewOverlay.OnSeekbarTouchListener() {

                    @Override
                    public void onTouchStart() {
                        if (mPlaybackStatus == PLAYBACK_STATUS_PLAYING) {
                            mWasPlayingBeforeTouch = true;
                            stopPlayback();
                        }
                    }

                    @Override
                    public void onTouchStop() {
                        if (mWasPlayingBeforeTouch) {
                            mWasPlayingBeforeTouch = false;
                            startPlayback();
                        }
                    }
                });

        View statsDrawer = rootView.findViewById(R.id.stats_drawer);
        statsDrawer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mChartController != null) {
                    mShowStatsOverlay = !mShowStatsOverlay;
                    mChartController.setShowStatsOverlay(mShowStatsOverlay);
                }
            }
        });

        mRunReviewPlaybackButton =
                (ImageButton) rootView.findViewById(R.id.run_review_playback_button);
        mRunReviewPlaybackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If playback is loading, don't do anything.
                if (mPlaybackStatus == PLAYBACK_STATUS_PLAYING) {
                    stopPlayback();
                } else if (mPlaybackStatus == PLAYBACK_STATUS_NOT_PLAYING){
                    startPlayback();
                }
            }
        });
        // Extend the size of the playback button (which is less than the a11y min size)
        // by letting it's parent call click events on the child.
        rootView.findViewById(R.id.run_review_playback_button_holder).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mRunReviewPlaybackButton.callOnClick();
                    }
                });

        final DataController dc = getDataController();
        dc.getExperimentRun(mStartLabelId,
                new LoggingConsumer<ExperimentRun>(TAG, "load experiment run") {
                    @Override
                    public void success(final ExperimentRun run) {
                        dc.getExperimentById(run.getExperimentId(),
                                new LoggingConsumer<Experiment>(TAG, "load experiment") {
                                    @Override
                                    public void success(Experiment experiment) {
                                        attachToRun(experiment, run, savedInstanceState);
                                    }
                                });
                    }
                });

        mScalarDisplayOptions = new ScalarDisplayOptions();

        mGraphOptionsController = new GraphOptionsController(getActivity());
        mGraphOptionsController.loadIntoScalarDisplayOptions(mScalarDisplayOptions, rootView);

        mChartController = new ChartController(ChartOptions.ChartPlacementType.TYPE_RUN_REVIEW,
                mScalarDisplayOptions);
        mChartController.setChartView((ChartView) rootView.findViewById(R.id.chart_view));
        mChartController.setProgressView((ProgressBar) rootView.findViewById(R.id.chart_progress));
        mChartController.setInteractionListener(mExternalAxis.getInteractionListener());
        mRunReviewOverlay.setChartController(mChartController);

        if (savedInstanceState != null) {
            if (getChildFragmentManager().findFragmentByTag(EditTimeDialog.TAG) != null) {
                setTimepickerUi(rootView, true);
            }
        }
        mAudioGenerator = new SimpleJsynAudioGenerator();

        return rootView;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == android.R.id.home) {
            Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
            if (mExperimentRun != null) {
                upIntent.putExtra(ExperimentDetailsFragment.ARG_EXPERIMENT_ID,
                        mExperimentRun.getExperimentId());
                upIntent.putExtra(ExperimentDetailsFragment.ARG_CREATE_TASK,
                        getArguments().getBoolean(ARG_CREATE_TASK, false));
                upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(), getView().getRootView().findViewById(R.id.app_bar_layout),
                        mStartLabelId);
                getActivity().startActivity(upIntent, options.toBundle());
            } else if (getActivity() != null) {
                // This is a weird error situation: we didn't load the experiment run at all.
                // In this case, just finish.
                getActivity().onBackPressed();
                return true;
            }
            return true;
        } else if (id == R.id.action_graph_options) {
            mGraphOptionsController.launchOptionsDialog(mScalarDisplayOptions,
                    new NewOptionsStorage.SnackbarFailureListener(getView()));
        } else if (id == R.id.action_export) {
            getDataController().getExperimentRun(mStartLabelId,
                new LoggingConsumer<ExperimentRun>(TAG, "retrieve argument") {
                    @Override
                    public void success(final ExperimentRun run) {
                        exportRun(run);
                    }
                });
        } else if (id == R.id.action_run_review_crop) {
            // TODO: Add crop functionality.
            AccessibilityUtils.makeSnackbar(getView(), getActivity().getResources().getString(
                            R.string.action_not_available), Snackbar.LENGTH_SHORT).show();
        } else if (id == R.id.action_run_review_add_note) {
            if (mExperimentRun != null) {
                launchLabelAdd(new GoosciLabelValue.LabelValue(), LABEL_TYPE_UNDECIDED,
                        Math.max(mRunReviewOverlay.getTimestamp(),
                                mExperimentRun.getFirstTimestamp()));
            }
        } else if (id == R.id.action_run_review_delete) {
            if (mExperimentRun != null) {
                deleteThisRun();
            }
        } else if (id == R.id.action_run_review_archive) {
            if (mExperimentRun != null) {
                setArchived(true);
            }
        } else if (id == R.id.action_run_review_unarchive) {
            if (mExperimentRun != null) {
                setArchived(false);
            }
        } else if (id == R.id.action_run_review_edit) {
            UpdateRunActivity.launch(getActivity(), mStartLabelId);
        } else if (id == R.id.action_enable_auto_zoom) {
            if (mExperimentRun != null) {
                setAutoZoomEnabled(true);
            }
        } else if (id == R.id.action_disable_auto_zoom) {
            if (mExperimentRun != null) {
                setAutoZoomEnabled(false);
            }
        } else if (id == R.id.action_run_review_audio_settings) {
            launchAudioSettings();
        }
        return super.onOptionsItemSelected(item);
    }

    private void setAutoZoomEnabled(boolean enableAutoZoom) {
        mExperimentRun.setAutoZoomEnabled(enableAutoZoom);
        getDataController().updateRun(mExperimentRun.getRun(),
                new LoggingConsumer<Success>(TAG, "update auto zoom") {
                    @Override
                    public void success(Success value) {
                        if (mCurrentSensorStats == null) {
                            AccessibilityUtils.makeSnackbar(getView(),
                                    getResources().getString(R.string.autozoom_failed),
                                    Snackbar.LENGTH_SHORT);
                        } else {
                            adjustYAxis();
                        }
                        getActivity().invalidateOptionsMenu();
                    }
        });
    }

    private void adjustYAxis() {
        if (mExperimentRun.getAutoZoomEnabled()) {
            double yMin = mCurrentSensorStats.getStat(StatsAccumulator.KEY_MIN);
            double yMax = mCurrentSensorStats.getStat(StatsAccumulator.KEY_MAX);
            mChartController.setYAxisWithBuffer(yMin, yMax);
        } else {
            GoosciSensorLayout.SensorLayout layout =
                    mExperimentRun.getSensorLayouts().get(mSelectedSensorIndex);
            mChartController.setYAxis(layout.minimumYAxisValue,
                    layout.maximumYAxisValue);
        }
        mChartController.refreshChartView();
        // Redraw the thumb after the chart is updated.
        mRunReviewOverlay.post(new Runnable() {
            public void run() {
                mRunReviewOverlay.refresh(false);
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_SENSOR_INDEX, mSelectedSensorIndex);
        outState.putBoolean(KEY_TIMESTAMP_EDIT_UI_VISIBLE, getChildFragmentManager()
                .findFragmentByTag(EditTimeDialog.TAG) != null);
        outState.putLong(KEY_EXTERNAL_AXIS_MINIMUM, mExternalAxis.getXMin());
        outState.putLong(KEY_EXTERNAL_AXIS_MAXIMUM, mExternalAxis.getXMax());
        outState.putLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP, mRunReviewOverlay.getTimestamp());
    }

    private void attachToRun(final Experiment experiment, final ExperimentRun run,
                             final Bundle savedInstanceState) {
        mExperimentRun = run;
        mExperiment = experiment;
        final View rootView = getView();
        if (rootView == null) {
            if (getActivity() != null) {
                ((AppCompatActivity) getActivity()).supportStartPostponedEnterTransition();
            }
            return;
        }
        final RecyclerView pinnedNoteList = (RecyclerView) rootView.findViewById(
                R.id.pinned_note_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        pinnedNoteList.setLayoutManager(layoutManager);

        final List<TextLabel> textNotes = run.getPinnedNotes();
        final List<PictureLabel> pictureNotes = run.getPictureLabels();
        final ArrayList<Label> combinedNotes = combinePinnedNotes(textNotes, pictureNotes);
        mPinnedNoteAdapter = new PinnedNoteAdapter(combinedNotes, run.getFirstTimestamp());
        mPinnedNoteAdapter.setListItemModifyListener(new PinnedNoteAdapter.ListItemEditListener() {
            @Override
            public void onListItemEdit(final Label item) {
                launchLabelEdit(item, run, item.getValue(), item.getTimeStamp());
            }

            @Override
            public void onListItemDelete(final Label item) {
                final DataController dc = getDataController();
                Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                        getActivity().getResources().getString(R.string.snackbar_note_deleted),
                        Snackbar.LENGTH_SHORT);

                // On undo, re-add the item to the database and the pinned note list.
                bar.setAction(R.string.snackbar_undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Label label;
                        if (item instanceof TextLabel) {
                            String title = ((TextLabel) item).getText();
                            label = new TextLabel(title, dc.generateNewLabelId(),
                                    item.getRunId(), item.getTimeStamp());
                        } else if (item instanceof PictureLabel) {
                            label = new PictureLabel(((PictureLabel) item).getFilePath(),
                                    ((PictureLabel) item).getCaption(), dc.generateNewLabelId(),
                                    item.getRunId(), item.getTimeStamp());
                        } else {
                            // Not a picture or a text label.
                            return;
                        }
                        label.setExperimentId(item.getExperimentId());
                        dc.addLabel(label, new LoggingConsumer<Label>(TAG, "re-add deleted label") {
                            @Override
                            public void success(Label label) {
                                mPinnedNoteAdapter.insertNote(label);
                                mChartController.setLabels(mPinnedNoteAdapter.getPinnedNotes());
                                WhistlePunkApplication.getUsageTracker(getActivity())
                                        .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                                TrackerConstants.ACTION_DELETE_UNDO,
                                                TrackerConstants.LABEL_RUN_REVIEW,
                                                TrackerConstants.getLabelValueType(item));
                            }
                        });
                    }
                });

                // Delete the item immediately, and remove it from the pinned note list.
                dc.deleteLabel(item, new LoggingConsumer<Success>(TAG, "delete label") {
                    @Override
                    public void success(Success value) {
                        mPinnedNoteAdapter.deleteNote(item);
                        mChartController.setLabels(mPinnedNoteAdapter.getPinnedNotes());
                        WhistlePunkApplication.getUsageTracker(getActivity())
                                .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                        TrackerConstants.ACTION_DELETED,
                                        TrackerConstants.LABEL_RUN_REVIEW,
                                        TrackerConstants.getLabelValueType(item));
                    }
                });
                bar.show();
            }
        });
        mPinnedNoteAdapter.setListItemClickListener(new PinnedNoteAdapter.ListItemClickListener() {
            @Override
            public void onListItemClicked(Label item) {
                if (item instanceof PictureLabel) {
                    PictureLabel pictureLabel = (PictureLabel) item;
                    launchPicturePreview(pictureLabel);
                }
            }
        });
        pinnedNoteList.setAdapter(mPinnedNoteAdapter);

        // Re-enable appropriate menu options.
        getActivity().invalidateOptionsMenu();

        hookUpExperimentDetailsArea(run, rootView);

        // Load the data for the first sensor only.
        // TODO: Consider pre-loading data for all the sensors instead of loading it when they
        // are selected.
        if (savedInstanceState != null) {
            mExternalAxis.zoomTo(savedInstanceState.getLong(KEY_EXTERNAL_AXIS_MINIMUM),
                    savedInstanceState.getLong(KEY_EXTERNAL_AXIS_MAXIMUM));
            long overlayTimestamp = savedInstanceState.getLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP);
            if (overlayTimestamp != RunReviewOverlay.NO_TIMESTAMP_SELECTED) {
                mRunReviewOverlay.setActiveTimestamp(overlayTimestamp);
            }
        }
        loadRunData(rootView);
        if (getActivity() != null) {
            ((AppCompatActivity) getActivity()).supportStartPostponedEnterTransition();
        }
    }

    private void deleteThisRun() {
        DeleteRunDialog dialog = DeleteRunDialog.newInstance();
        dialog.show(getChildFragmentManager(), DeleteRunDialog.TAG);
    }

    private void setArchived(final boolean archived) {
        mExperimentRun.setArchived(archived);
        getDataController().updateRun(mExperimentRun.getRun(), new LoggingConsumer<Success>(TAG,
                "Editing run") {
            @Override
            public void success(Success value) {
                Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                        getActivity().getResources().getString(archived ?
                                R.string.archived_run_message : R.string.unarchived_run_message),
                        Snackbar.LENGTH_LONG);

                if (archived) {
                    bar.setAction(R.string.action_undo, new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            setArchived(false);
                        }
                    });
                }
                bar.show();
                getActivity().invalidateOptionsMenu();

                setArchivedUi(getView(), archived);
            }
        });
        WhistlePunkApplication.getUsageTracker(getActivity())
                .trackEvent(TrackerConstants.CATEGORY_RUNS,
                        archived ? TrackerConstants.ACTION_ARCHIVE :
                                TrackerConstants.ACTION_UNARCHIVE,
                        null, 0);
    }

    // TODO: After loading run data, store it locally so that it does not need to be re-loaded in
    // the case of switching between multiple sensors or rotating.
    private void loadRunData(final View rootView) {
        stopPlayback();
        final GoosciSensorLayout.SensorLayout sensorLayout = getSensorLayout();
        populateSensorViews(rootView, sensorLayout);
        updateSwitchSensorArrows(rootView, mExperimentRun.getSensorTags(), sensorLayout.sensorId);

        // Load the sonificationType which was saved in the layout.
        // TODO: Create a menu option in RunReview to change this.
        String sonificationType = getSonificationType(sensorLayout);

        mAudioGenerator.setSonificationType(sonificationType);

        final long firstTimestamp = mExperimentRun.getFirstTimestamp();
        final long lastTimestamp = mExperimentRun.getLastTimestamp();
        mRunReviewOverlay.setVisibility(View.INVISIBLE);

        final DataController dataController = getDataController();

        mCurrentSensorStats = null;
        final StatsList statsList = (StatsList) rootView.findViewById(R.id.stats_drawer);
        dataController.getStats(mExperimentRun.getRunId(), sensorLayout.sensorId,
                new LoggingConsumer<RunStats>(TAG, "load stats") {
                    @Override
                    public void success(final RunStats runStats) {
                        mCurrentSensorStats = runStats;
                        List<StreamStat> streamStats =
                                new StatsAccumulator.StatsDisplay().updateStreamStats(runStats);
                        statsList.updateStats(streamStats);


                        // TODO: Replace with chartLoadedCallback and loadRunData when b/29539231
                        // is fixed instead of the above.
                        tryLoadingChartData(firstTimestamp, lastTimestamp, sensorLayout,
                                streamStats);
                    }
                });
    }

    private String getSonificationType(GoosciSensorLayout.SensorLayout sensorLayout) {
        return LocalSensorOptionsStorage.loadFromLayoutExtras(sensorLayout).getReadOnly().getString(
                ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE,
                ScalarDisplayOptions.DEFAULT_SONIFICATION_TYPE);
    }

    @Override
    public void requestDeleteRun() {
        getDataController().deleteRun(mExperimentRun.getRunId(),
                new LoggingConsumer<Success>(TAG, "Deleting new experiment") {
                    @Override
                    public void success(Success value) {
                        // Go back to the observe & record.
                        Intent intent = new Intent(getActivity(), RecordFragment.class);
                        NavUtils.navigateUpTo(getActivity(), intent);
                    }
                });
    }

    // TODO: Remove this in favor of mChartController.loadRunData above when b/29539231 is resolved.
    private void tryLoadingChartData(final long firstTimestamp, final long lastTimestamp,
            final GoosciSensorLayout.SensorLayout sensorLayout,
            final List<StreamStat> streamStats) {
        // If we are currently trying to load something, don't try and load something else.
        // Instead, when loading is completed a callback will check that the correct data
        // was loaded and re-call this function.
        // The status is always set loading before loadSensorReadings, and always set to idle
        // when loading is completed (regardless of whether the correct data was loaded).
        if (mLoadingStatus != GRAPH_LOAD_STATUS_IDLE) {
            return;
        }
        mChartController.updateColor(sensorLayout.color);
        mRunReviewOverlay.updateColor(sensorLayout.color);
        mRunReviewPlaybackButton.setVisibility(View.INVISIBLE);

        final long previousXMin = mExternalAxis.getXMin();
        final long previousXMax = mExternalAxis.getXMax();
        final long overlayTimestamp = mRunReviewOverlay.getTimestamp();

        mChartController.clearData();
        setGraphLoadStatus(GRAPH_LOAD_STATUS_LOADING);
        mChartController.setShowProgress(true);
        ScalarDataLoader.loadSensorReadings(sensorLayout.sensorId, getDataController(),
                firstTimestamp, lastTimestamp, 0, new Runnable() {
                    public void run() {
                        setGraphLoadStatus(GRAPH_LOAD_STATUS_IDLE);
                        GoosciSensorLayout.SensorLayout selectedSensorLayout = getSensorLayout();
                        if (!TextUtils.equals(sensorLayout.sensorId,
                                selectedSensorLayout.sensorId)) {
                            // The wrong sensor ID was loaded into this lineGraphPresenter. Clear
                            // and try again with the updated sensor ID.
                            mChartController.clearData();
                            tryLoadingChartData(firstTimestamp, lastTimestamp,
                                    selectedSensorLayout, streamStats);
                            return;
                        }
                        // Show the replay play button
                        mRunReviewPlaybackButton.setVisibility(View.VISIBLE);


                        // Add the labels after all the data is loaded
                        // so that they are interpolated correctly.
                        mChartController.setLabels(mPinnedNoteAdapter.getPinnedNotes());
                        mChartController.updateStats(streamStats);
                        mChartController.setShowProgress(false);

                        // Buffer the endpoints a bit so they look nice.
                        long buffer = (long) (ExternalAxisController.EDGE_POINTS_BUFFER_FRACTION *
                                (lastTimestamp - firstTimestamp));
                        long renderedXMin = firstTimestamp - buffer;
                        long renderedXMax = lastTimestamp + buffer;
                        if (previousXMax == Long.MIN_VALUE) {
                            // This is the first load. Zoom to fit the run.
                            mChartController.setXAxis(renderedXMin, renderedXMax);
                            // Display the the graph and overlays.
                            mExternalAxis.setReviewData(firstTimestamp,
                                    renderedXMin, renderedXMax, mRunReviewOverlay.getSeekbar());
                            mExternalAxis.zoomTo(mChartController.getRenderedXMin(),
                                    mChartController.getRenderedXMax());
                            mRunReviewOverlay.setActiveTimestamp(firstTimestamp);
                        } else {
                            mExternalAxis.zoomTo(previousXMin, previousXMax);
                            mExternalAxis.setReviewData(firstTimestamp,
                                    renderedXMin, renderedXMax, mRunReviewOverlay.getSeekbar());
                        }
                        adjustYAxis();

                        if (overlayTimestamp != RunReviewOverlay.NO_TIMESTAMP_SELECTED) {
                            mRunReviewOverlay.setActiveTimestamp(overlayTimestamp);
                            mRunReviewOverlay.setVisibility(View.VISIBLE);
                        }
                    }
                }, LoggingConsumer.expectSuccess(TAG, "Load data for RunReview"),
                mChartController);
    }

    // TODO(saff): probably extract ExperimentRunPresenter
    private void hookUpExperimentDetailsArea(ExperimentRun run, View rootView) {
        setArchivedUi(rootView, run.isArchived());

        final TextView runTitle = (TextView) rootView.findViewById(R.id.run_title_text);
        runTitle.setText(run.getRunTitle(rootView.getContext()));

        final TextView runDetailsText = (TextView) rootView.findViewById(R.id.run_details_text);
        runDetailsText.setText(run.getDisplayTime(runDetailsText.getContext()));

        final TextView durationText = (TextView) rootView.findViewById(R.id.run_review_duration);
        ElapsedTimeFormatter formatter = ElapsedTimeFormatter.getInstance(
                durationText.getContext());
        durationText.setText(formatter.format(
                run.elapsedSeconds()));
        durationText.setContentDescription(formatter.formatForAccessibility(run.elapsedSeconds()));
    }

    private void setArchivedUi(View rootView, boolean isArchived) {
        View archivedIndicator = rootView.findViewById(R.id.light_archived_indicator);
        if (isArchived) {
            archivedIndicator.setVisibility(View.VISIBLE);
            archivedIndicator.setAlpha(
                    getResources().getFraction(R.fraction.metadata_card_archived_alpha, 1, 1));
        } else {
            archivedIndicator.setVisibility(View.GONE);
        }
    }

    // Shows and hides the switch sensor arrows based on our current position with sensorIds.
    private void updateSwitchSensorArrows(final View rootView, final List<String> sensorIds,
            final String sensorId) {
        ImageButton prevButton = (ImageButton) rootView.findViewById(
                R.id.run_review_switch_sensor_btn_prev);
        ImageButton nextButton = (ImageButton) rootView.findViewById(
                R.id.run_review_switch_sensor_btn_next);

        final int position = sensorIds.indexOf(sensorId);
        boolean hasPrevButton = position > 0;
        boolean hasNextButton = position < sensorIds.size() - 1;

        prevButton.setVisibility(hasPrevButton ? View.VISIBLE : View.INVISIBLE);
        nextButton.setVisibility(hasNextButton ? View.VISIBLE : View.INVISIBLE);

        if (hasPrevButton) {
            prevButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSelectedSensorIndex = position - 1;
                    loadRunData(rootView);
                }
            });
            String prevSensorId = sensorIds.get(position - 1);
            updateContentDescription(prevButton, R.string.run_review_switch_sensor_btn_prev,
                    prevSensorId, getActivity());
        }
        if (hasNextButton) {
            nextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSelectedSensorIndex = position + 1;
                    loadRunData(rootView);
                }
            });
            String nextSensorId = sensorIds.get(position + 1);
            updateContentDescription(nextButton, R.string.run_review_switch_sensor_btn_next,
                    nextSensorId, getActivity());
        }
    }

    public static void updateContentDescription(ImageButton button, int stringId, String sensorId,
                                                Context context) {
        SensorAppearance appearance = AppSingleton.getInstance(context)
                .getSensorAppearanceProvider()
                .getAppearance(sensorId);
        String content = context.getResources().getString(stringId, appearance.getName(context));
        button.setContentDescription(content);
    }

    private void populateSensorViews(View rootView, GoosciSensorLayout.SensorLayout sensorLayout) {
        final Context context = rootView.getContext();
        final SensorAppearance appearance = AppSingleton.getInstance(context)
                .getSensorAppearanceProvider()
                .getAppearance(sensorLayout.sensorId);
        final TextView sensorNameText = (TextView) rootView.findViewById(
                R.id.run_review_sensor_name);
        sensorNameText.setText(appearance.getSensorDisplayName(context));
        final ImageView sensorIconImage = (ImageView) rootView.findViewById(
                R.id.sensor_icon);
        appearance.applyDrawableToImageView(sensorIconImage, sensorLayout.color);
        mRunReviewOverlay.setUnits(appearance.getUnits(context));
    }

    private void launchLabelAdd(GoosciLabelValue.LabelValue selectedValue, int labelType,
            long timestamp) {
        String labelTimeText =
                PinnedNoteAdapter.getNoteTimeText(timestamp, mExperimentRun.getFirstTimestamp());
        AddNoteDialog dialog = AddNoteDialog.newInstance(timestamp, mExperimentRun.getRunId(),
                mExperimentRun.getExperimentId(), R.string.add_note_hint_text,
                /* show timestamp section */ true, labelTimeText, selectedValue, labelType);
        dialog.show(getChildFragmentManager(), AddNoteDialog.TAG);
    }

    @Override
    public LoggingConsumer<Label> onLabelAdd() {
        return  new LoggingConsumer<Label>(TAG, "add label") {
            @Override
            public void success(Label label) {
                mPinnedNoteAdapter.insertNote(label);
                mChartController.setLabels(mPinnedNoteAdapter.getPinnedNotes());
                WhistlePunkApplication.getUsageTracker(getActivity())
                        .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                TrackerConstants.ACTION_CREATE,
                                TrackerConstants.LABEL_RUN_REVIEW,
                                TrackerConstants.getLabelValueType(label));
            }
        };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PictureUtils.REQUEST_TAKE_PHOTO) {
            Fragment dialog = getChildFragmentManager().findFragmentByTag(AddNoteDialog.TAG);
            if (dialog != null) {
                dialog.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    private void launchPicturePreview(PictureLabel label) {
        PreviewNoteDialog dialog = PreviewNoteDialog.newInstance(label);
        dialog.show(getChildFragmentManager(), EditNoteDialog.TAG);
    }

    @Override
    public MaybeConsumer<Label> onLabelEdit(final Label label) {
        return new LoggingConsumer<Label>(TAG, "edit label") {
            @Override
            public void success(Label value) {
                mPinnedNoteAdapter.editLabel(value);
                // The timestamp may have been edited, so also refresh the line graph presenter.
                mChartController.setLabels(mPinnedNoteAdapter.getPinnedNotes());
                WhistlePunkApplication.getUsageTracker(getActivity())
                        .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                TrackerConstants.ACTION_EDITED,
                                TrackerConstants.LABEL_RUN_REVIEW,
                                TrackerConstants.getLabelValueType(label));
            }
        };
    }

    @Override
    public void onAddNoteTimestampClicked(GoosciLabelValue.LabelValue selectedValue, int labelType,
            long selectedTimestamp) {
        AddNoteDialog addDialog = (AddNoteDialog) getChildFragmentManager()
                .findFragmentByTag(AddNoteDialog.TAG);
        if (addDialog != null) {
            addDialog.dismiss();
        }

        // Show the timestamp edit window below the graph / over the notes
        getView().findViewById(R.id.embedded).setVisibility(View.VISIBLE);
        EditTimeDialog timeDialog = EditTimeDialog.newInstance(selectedValue, labelType,
                selectedTimestamp, mExperimentRun.getFirstTimestamp());
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.add(R.id.embedded, timeDialog, EditTimeDialog.TAG);
        ft.commit();
        mRunReviewOverlay.setActiveTimestamp(selectedTimestamp);
        mRunReviewOverlay.setOnTimestampChangeListener(timeDialog);
        setTimepickerUi(getView(), true);
    }

    @Override
    public void onEditNoteTimestampClicked(Label label, GoosciLabelValue.LabelValue selectedValue,
            long labelTimestamp) {
        // Dismiss the edit note dialog and show the timestamp dialog.
        EditNoteDialog editDialog = (EditNoteDialog) getChildFragmentManager()
                .findFragmentByTag(EditNoteDialog.TAG);
        if (editDialog != null) {
            editDialog.dismiss();
        }

        // Show the timestamp edit window below the graph / over the notes
        getView().findViewById(R.id.embedded).setVisibility(View.VISIBLE);
        EditTimeDialog timeDialog = EditTimeDialog.newInstance(label, selectedValue, labelTimestamp,
                mExperimentRun.getFirstTimestamp());
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.add(R.id.embedded, timeDialog, EditTimeDialog.TAG);
        ft.commit();
        mRunReviewOverlay.setActiveTimestamp(labelTimestamp);
        mRunReviewOverlay.setOnTimestampChangeListener(timeDialog);
        setTimepickerUi(getView(), true);
    }

    private void setTimepickerUi(View rootView, boolean showTimepicker) {
        if (showTimepicker) {
            mActionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(
                    new ActionMode.Callback() {
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    mode.setTitle(getResources().getString(R.string.edit_note_time));
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    if (mActionMode != null) {
                        mActionMode = null;
                        dismissEditTimeDialog();
                    }
                }
            });

            rootView.findViewById(R.id.embedded).setVisibility(View.VISIBLE);

            // Collapse app bar layout as much as possible to bring the graph to the top.
            AppBarLayout appBarLayout = (AppBarLayout) rootView.findViewById(R.id.app_bar_layout);
            appBarLayout.setExpanded(false);
            setFrozen(rootView, true);

            // Show a grey overlay over the notes. Make it so users can cancel the dialog
            // by clicking in the grey overlay to simulate a normal dialog.
            View notesOverlay = rootView.findViewById(R.id.pinned_note_overlay);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // On Kitkat devices, the pinned note list is shown over the AppBarLayout
                // instead of under it. We can manually adjust the layout params to show it
                // in the right location and at the right size to cover just the pinned note
                // list.
                ViewGroup.MarginLayoutParams params = (CoordinatorLayout.LayoutParams)
                        rootView.findViewById(R.id.pinned_note_list).getLayoutParams();
                params.setMargins(0, 0, 0, 0);
                notesOverlay.setLayoutParams(params);
                notesOverlay.setPadding(0, 0, 0, 0);
            }

            notesOverlay.setVisibility(View.VISIBLE);
            notesOverlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mActionMode.finish();
                }
            });

            EditTimeDialog dialog = (EditTimeDialog) getChildFragmentManager()
                    .findFragmentByTag(EditTimeDialog.TAG);
            if (dialog != null) {
                mRunReviewOverlay.setActiveTimestamp(dialog.getCurrentTimestamp());
                mRunReviewOverlay.setOnTimestampChangeListener(dialog);
            }
        } else {
            if (mActionMode != null) {
                mActionMode.finish();
            }
            setFrozen(rootView, false);
            rootView.findViewById(R.id.pinned_note_overlay).setVisibility(View.GONE);
            rootView.findViewById(R.id.embedded).setVisibility(View.GONE);
        }
    }

    private void dismissEditTimeDialog() {
        EditTimeDialog dialog = (EditTimeDialog) getChildFragmentManager()
                .findFragmentByTag(EditTimeDialog.TAG);
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    private void setFrozen(View rootView, boolean isFrozen) {
        ((FreezeableCoordinatorLayout) rootView).setFrozen(isFrozen);
    }

    @Override
    public void onEditTimeDialogDismissedEdit(Label label,
            GoosciLabelValue.LabelValue selectedValue, long selectedTimestamp) {
        setTimepickerUi(getView(), false);
        launchLabelEdit(label, mExperimentRun, selectedValue, selectedTimestamp);
    }

    @Override
    public void onEditTimeDialogDismissedAdd(GoosciLabelValue.LabelValue selectedValue,
            int labelType, long selectedTimestamp) {
        setTimepickerUi(getView(), false);
        launchLabelAdd(selectedValue, labelType, selectedTimestamp);
    }

    private void launchLabelEdit(Label label, ExperimentRun run,
            GoosciLabelValue.LabelValue newValue, long selectedTimestamp) {
        String labelTimeText =
                PinnedNoteAdapter.getNoteTimeText(selectedTimestamp, run.getFirstTimestamp());
        EditNoteDialog dialog = EditNoteDialog.newInstance(label, newValue, labelTimeText,
                selectedTimestamp);
        dialog.show(getChildFragmentManager(), EditNoteDialog.TAG);
    }

    private void launchAudioSettings() {
        stopPlayback();

        List<GoosciSensorLayout.SensorLayout> sensorLayouts = mExperimentRun.getSensorLayouts();
        int size = sensorLayouts.size();
        String[] sensorIds = new String[size];
        String[] sonificationTypes = new String[size];
        for (int i = 0; i < size; i++) {
            GoosciSensorLayout.SensorLayout layout = sensorLayouts.get(i);
            sensorIds[i] = layout.sensorId;
            sonificationTypes[i] = getSonificationType(layout);
        }
        AudioSettingsDialog dialog = AudioSettingsDialog.newInstance(sonificationTypes, sensorIds,
                mSelectedSensorIndex);
        dialog.show(getChildFragmentManager(), AudioSettingsDialog.TAG);
    }

    @Override
    public void onAudioSettingsPreview(String[] previewSonificationTypes, String[] sensorIds) {
        // RunReview does not have audio preview.
    }

    @Override
    public void onAudioSettingsApplied(String[] newSonificationTypes, String[] sensorIds) {
        // Update the currently selected sonification type.
        mAudioGenerator.setSonificationType(newSonificationTypes[mSelectedSensorIndex]);

        // Save the new sonification types into their respective sensorLayouts.
        // Note that this uses the knowledge that the sensor ordering has not changed since
        // launchAudioSettings.
        List<GoosciSensorLayout.SensorLayout> layouts = mExperimentRun.getSensorLayouts();
        int size = layouts.size();
        for (int i = 0; i < size; i++) {
            // Update the sonification setting in the extras for this layout.
            LocalSensorOptionsStorage storage = new LocalSensorOptionsStorage();
            storage.putAllExtras(layouts.get(i).extras);
            storage.load(LoggingConsumer.expectSuccess(TAG, "loading sensor layout")).put(
                    ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE, newSonificationTypes[i]);
            layouts.get(i).extras = storage.exportAsLayoutExtras();
        }

        // Save the updated layouts to the DB.
        getDataController().updateRun(mExperimentRun.getRun(),
                LoggingConsumer.<Success>expectSuccess(TAG, "updating audio settings"));
    }

    @Override
    public void onAudioSettingsCanceled(String[] originalSonificationTypes, String[] sensorIds) {
        // RunReview does not have audio preview.
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    // Combines two lists of notes into one list in chronological order.
    private ArrayList<Label> combinePinnedNotes(List<TextLabel> textNotes,
                                                List<PictureLabel> pictureNotes) {
        ArrayList<Label> pinnedNotes = new ArrayList<>();
        pinnedNotes.addAll(textNotes);
        pinnedNotes.addAll(pictureNotes);
        Collections.sort(pinnedNotes, new Comparator<Label>() {
            @Override
            public int compare(Label lhs, Label rhs) {
                return (int) (lhs.getTimeStamp() - rhs.getTimeStamp());
            }
        });
        return pinnedNotes;
    }

    // TODO: Split playback into a separate object from RunReview.
    private void startPlayback() {
        if (!mChartController.hasDrawnChart() || mPlaybackStatus != PLAYBACK_STATUS_NOT_PLAYING) {
            return;
        }

        final double yMin = mChartController.getRenderedYMin();
        final double yMax = mChartController.getRenderedYMax();
        final long xMax = mExperimentRun.getLastTimestamp();
        final List<ChartData.DataPoint> audioData = new ArrayList<>();


        final DataController dataController = getDataController();
        final String selectedSensorId =
                mExperimentRun.getSensorLayouts().get(mSelectedSensorIndex).sensorId;
        long xMinToLoad = mRunReviewOverlay.getTimestamp();
        if (xMinToLoad == RunReviewOverlay.NO_TIMESTAMP_SELECTED) {
            xMinToLoad = mExperimentRun.getFirstTimestamp();
        } else {
            if ((xMax - xMinToLoad) < .05 * (xMax - mExperimentRun.getFirstTimestamp())) {
                // If we are 95% or more towards the end, start at the beginning.
                // This allows for some slop.
                xMinToLoad = mExperimentRun.getFirstTimestamp();
            }
        }
        long xMaxToLoad = Math.min(xMinToLoad + DURATION_MS_PER_AUDIO_PLAYBACK_LOAD,
                xMax);
        final boolean fullyLoaded = xMaxToLoad == xMax;

        mHandler = new Handler();
        mPlaybackRunnable = new Runnable() {
            boolean mFullyLoaded = fullyLoaded;
            @Override
            public void run() {
                if (audioData.size() == 0) {
                    mPlaybackStatus = PLAYBACK_STATUS_NOT_PLAYING;
                    return;
                }

                // Every time we play a data point, we remove it from the list.
                ChartData.DataPoint point = audioData.remove(0);
                long timestamp = point.getX();

                // Load more data when needed, i.e. when we are within a given duration away from
                // the last loaded timestamp, and we aren't fully loaded yet.
                long lastTimestamp = audioData.get(audioData.size() - 1).getX();
                if (timestamp + DURATION_MS_PER_AUDIO_PLAYBACK_LOAD / 2 > lastTimestamp &&
                        !mFullyLoaded) {
                    long xMaxToLoad =
                            Math.min(lastTimestamp + DURATION_MS_PER_AUDIO_PLAYBACK_LOAD, xMax);
                    mFullyLoaded = xMaxToLoad == xMax;
                    dataController.getScalarReadings(selectedSensorId, /* tier 0 */ 0,
                            TimeRange.oldest(Range.openClosed(lastTimestamp, xMaxToLoad)),
                            DATAPOINTS_PER_AUDIO_PLAYBACK_LOAD,
                            new MaybeConsumer<ScalarReadingList>() {
                                @Override
                                public void success(ScalarReadingList list) {
                                    audioData.addAll(list.asDataPoints());
                                }

                                @Override
                                public void fail(Exception e) {
                                    Log.e(TAG, "Error loading audio playback data");
                                    stopPlayback();
                                }
                            });
                }

                // Now play the tone, and get set up for the next callback, if one is needed.
                try {
                    mAudioGenerator.addData(timestamp, point.getY(), yMin, yMax);
                    mRunReviewOverlay.setActiveTimestamp(timestamp);
                } finally {
                    // If this is the second to last point, some special handling
                    // needs to be done to determine when to make the last tone.
                    if (audioData.size() > 2) {
                        // Play the next note after the time between this point and the
                        // next point has elapsed.
                        // mPlaybackIndex is now the index of the next point.
                        mHandler.postDelayed(mPlaybackRunnable,
                                audioData.get(0).getX() - timestamp);
                    } else if (audioData.size() == 1) {
                        // The last note gets some duration.
                        mHandler.postDelayed(mPlaybackRunnable, 10);
                    } else {
                        stopPlayback();
                    }
                }
            }
        };

        // Load the first set of scalar readings, and start playing as soon as they are loaded.
        dataController.getScalarReadings(selectedSensorId, /* tier 0 */ 0,
                TimeRange.oldest(Range.closed(xMinToLoad, xMaxToLoad)),
                DATAPOINTS_PER_AUDIO_PLAYBACK_LOAD, new MaybeConsumer<ScalarReadingList>() {
                    @Override
                    public void success(ScalarReadingList list) {
                        audioData.addAll(list.asDataPoints());
                        mAudioGenerator.startPlaying();
                        mPlaybackRunnable.run();
                        mPlaybackStatus = PLAYBACK_STATUS_PLAYING;
                        mRunReviewPlaybackButton.setImageDrawable(
                                getResources().getDrawable(R.drawable.ic_pause_black_24dp));
                        mRunReviewPlaybackButton.setContentDescription(
                                getResources().getString(R.string.playback_button_pause));
                    }

                    @Override
                    public void fail(Exception e) {
                        if (Log.isLoggable(TAG, Log.ERROR)) {
                            Log.e(TAG, "Error loading audio playback data");
                            stopPlayback();
                        }
                    }
                });

        mPlaybackStatus = PLAYBACK_STATUS_LOADING;
    }

    private void stopPlayback() {
        if (mPlaybackStatus == PLAYBACK_STATUS_NOT_PLAYING) {
            return;
        }
        mHandler.removeCallbacks(mPlaybackRunnable);
        mAudioGenerator.stopPlaying();

        mRunReviewPlaybackButton.setImageDrawable(
                getResources().getDrawable(R.drawable.ic_play_arrow_black_24dp));
        mRunReviewPlaybackButton.setContentDescription(
                getResources().getString(R.string.playback_button_play));
        mPlaybackStatus = PLAYBACK_STATUS_NOT_PLAYING;
    }

    private void exportRun(final ExperimentRun run) {
        mRunReviewExporter.startExport(getActivity(), mExperiment.getDisplayTitle(
                getActivity()), run, run.getSensorLayouts().get(mSelectedSensorIndex).sensorId);
        // Disable the item.
        getActivity().invalidateOptionsMenu();
    }

    private void showExportUi() {
        mExportProgress.setMax(100);
        mExportProgress.setProgress(0);
        mExportProgress.setVisibility(View.VISIBLE);
    }

    private void setExportProgress(final int progress) {
        if (getView() == null) {
            return;
        }
        getView().post(new Runnable() {
            @Override
            public void run() {
                mExportProgress.setProgress(progress);
            }
        });
    }

    private void resetExportUi() {
        if (getView() == null) {
            return;
        }
        getView().post(new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().invalidateOptionsMenu();
                }
                mExportProgress.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public int getGraphLoadStatus() {
        return mLoadingStatus;
    }

    @Override
    public void setGraphLoadStatus(int graphLoadStatus) {
        mLoadingStatus = graphLoadStatus;
    }

    @Override
    public String getRunId() {
        return mExperimentRun.getRunId();
    }

    @Override
    public GoosciSensorLayout.SensorLayout getSensorLayout() {
        return mExperimentRun.getSensorLayouts().get(mSelectedSensorIndex);
    }
}