package de.danoeh.antennapodsp.fragment;

import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import de.danoeh.antennapodsp.AppConfig;
import de.danoeh.antennapodsp.R;
import de.danoeh.antennapodsp.activity.MainActivity;
import de.danoeh.antennapodsp.asynctask.ImageLoader;
import de.danoeh.antennapodsp.service.playback.PlaybackService;
import de.danoeh.antennapodsp.util.Converter;
import de.danoeh.antennapodsp.util.ShareUtils;
import de.danoeh.antennapodsp.util.playback.Playable;
import de.danoeh.antennapodsp.util.playback.PlaybackController;

/**
 * Fragment which is supposed to be displayed outside of the MediaplayerActivity
 * if the PlaybackService is running
 */
public class ExternalPlayerFragment extends Fragment {
    private static final String TAG = "ExternalPlayerFragment";

    public static final String ARG_INITIAL_STATE = "argInitialState";
    public static final int ARG_INIT_EPXANDED = 1;
    public static final int ARG_INIT_ANCHORED = 0;

    private ViewGroup fragmentLayout;

    public static enum FragmentState {EXPANDED, ANCHORED};

    // expanded layout components
    private LinearLayout topviewExpanded;
    private RelativeLayout layoutInfoExpanded;
    private ImageView imgvCoverExpanded;
    private TextView txtvTitleExpanded;
    private ImageButton butActionExpanded;
    private ImageButton butPlayExpanded;
    private ImageButton butRevExpanded;
    private ImageButton butFFExpanded;
    private TextView txtvPositionExpanded;
    private TextView txtvLengthExpanded;
    private TextView txtvDescriptionExpanded;
    private SeekBar sbPositionExanded;


    // anchored layout components
    private LinearLayout topViewAnchored;
    private ImageView imgvCoverAnchored;
    private ViewGroup layoutInfoAnchored;
    private TextView txtvTitleAnchored;
    private TextView txtvStatusAnchored;
    private ImageButton butPlayAnchored;

    private PlaybackController controller;

    private FragmentState fragmentState;

    public static ExternalPlayerFragment newInstance(int initialState) {
        if (initialState != ARG_INIT_ANCHORED && initialState != ARG_INIT_EPXANDED)
            throw new IllegalArgumentException("invalid initial state");

        Bundle b = new Bundle();
        b.putInt(ARG_INITIAL_STATE, initialState);
        ExternalPlayerFragment f = new ExternalPlayerFragment();
        f.setArguments(b);
        return f;
    }

    public void setFragmentState(FragmentState fs) {
        boolean stateChanged = fs != fragmentState;
        fragmentState = fs;
        if (topViewAnchored != null && topviewExpanded != null) {
            if (fs == FragmentState.ANCHORED) {
                topviewExpanded.setVisibility(View.GONE);
                topViewAnchored.setVisibility(View.VISIBLE);
            } else {
                topViewAnchored.setVisibility(View.GONE);
                topviewExpanded.setVisibility(View.VISIBLE);
            }
        }
        if (stateChanged && controller != null) {
            controller.repeatHandleStatus();
        }

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int state = getArguments().getInt(ARG_INITIAL_STATE);
        if (state == ARG_INIT_ANCHORED) {
            fragmentState = FragmentState.ANCHORED;
        } else if (state == ARG_INIT_EPXANDED) {
            fragmentState = FragmentState.EXPANDED;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.external_player_fragment,
                container, false);
        fragmentLayout = (ViewGroup) root.findViewById(R.id.fragmentLayout);
        // Expanded components
        topviewExpanded = (LinearLayout) root.findViewById(R.id.topviewExpanded);
        imgvCoverExpanded = (ImageView) root.findViewById(R.id.imgvCoverExpanded);
        txtvTitleExpanded = (TextView) root.findViewById(R.id.txtvTitleExpanded);
        butActionExpanded = (ImageButton) root.findViewById(R.id.butActionExpanded);
        butPlayExpanded = (ImageButton) root.findViewById(R.id.butPlayExpanded);
        butRevExpanded = (ImageButton) root.findViewById(R.id.butRevExpanded);
        butFFExpanded = (ImageButton) root.findViewById(R.id.butFFExpanded);
        txtvPositionExpanded = (TextView) root.findViewById(R.id.txtvPositionExpanded);
        txtvLengthExpanded = (TextView) root.findViewById(R.id.txtvLengthExpanded);
        sbPositionExanded = (SeekBar) root.findViewById(R.id.sbPositionExpanded);
        txtvDescriptionExpanded = (TextView) root.findViewById(R.id.txtvDescriptionExpanded);
        layoutInfoExpanded = (RelativeLayout) root.findViewById(R.id.layoutInfo_expanded);

        // Anchored components
        topViewAnchored = (LinearLayout) root.findViewById(R.id.topviewAnchored);
        imgvCoverAnchored = (ImageView) root.findViewById(R.id.imgvCoverAnchored);
        layoutInfoAnchored = (ViewGroup) root.findViewById(R.id.layoutInfo_anchored);
        txtvTitleAnchored = (TextView) root.findViewById(R.id.txtvTitleAnchored);
        butPlayAnchored = (ImageButton) root.findViewById(R.id.butPlayAnchored);
        txtvStatusAnchored = (TextView) root.findViewById(R.id.txtvStatusAnchored);

        layoutInfoAnchored.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (AppConfig.DEBUG)
                    Log.d(TAG, "layoutInfo was clicked");

                if (controller.getMedia() != null) {
                    startActivity(PlaybackService.getPlayerActivityIntent(
                            getActivity(), controller.getMedia()));
                }
            }
        });
        ((MainActivity)getActivity()).onPlayerFragmentCreated(this, fragmentState);
        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        controller = setupPlaybackController();
        butPlayExpanded.setOnClickListener(controller.newOnPlayButtonClickListener());
        butPlayAnchored.setOnClickListener(controller.newOnPlayButtonClickListener());

        sbPositionExanded.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            float prog = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prog = controller.onSeekBarProgressChanged(seekBar, progress, fromUser, txtvPositionExpanded);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                controller.onSeekBarStartTrackingTouch(seekBar);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                controller.onSeekBarStopTrackingTouch(seekBar, prog);
                prog = 0;
            }
        });

        butRevExpanded.setOnClickListener(controller.newOnRevButtonClickListener());
        butFFExpanded.setOnClickListener(controller.newOnFFButtonClickListener());

        butActionExpanded.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Playable media;
                if (controller != null && (media = controller.getMedia()) != null && media.getWebsiteLink() != null) {
                    Uri uri = Uri.parse(media.getWebsiteLink());
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
            }
        });

        setFragmentState(fragmentState);
    }

    private PlaybackController setupPlaybackController() {
        return new PlaybackController(getActivity(), true) {

            @Override
            public void setupGUI() {
            }

            @Override
            public void onPositionObserverUpdate() {
                int duration = controller.getDuration();
                int position = controller.getPosition();
                if (duration != PlaybackController.INVALID_TIME
                        && position != PlaybackController.INVALID_TIME) {
                    txtvPositionExpanded.setText(Converter.getDurationStringLong(position));
                    txtvLengthExpanded.setText(Converter.getDurationStringLong(duration));
                }
            }

            @Override
            public void onReloadNotification(int code) {
            }

            @Override
            public void onBufferStart() {
                // TODO Auto-generated method stub

            }

            @Override
            public void onBufferEnd() {
                // TODO Auto-generated method stub

            }

            @Override
            public void onBufferUpdate(float progress) {
            }

            @Override
            public void onSleepTimerUpdate() {
            }

            @Override
            public void handleError(int code) {
            }

            @Override
            public ImageButton getPlayButton() {
                if (fragmentState == FragmentState.EXPANDED)
                    return butPlayExpanded;
                else
                    return butPlayAnchored;

            }

            @Override
            public void postStatusMsg(int msg) {
                txtvStatusAnchored.setText(msg);
            }

            @Override
            public void clearStatusMsg() {
                txtvStatusAnchored.setText("");
            }

            @Override
            public boolean loadMediaInfo() {
                ExternalPlayerFragment fragment = ExternalPlayerFragment.this;
                if (fragment != null) {
                    return fragment.loadMediaInfo();
                } else {
                    return false;
                }
            }

            @Override
            public void onAwaitingVideoSurface() {
            }

            @Override
            public void onServiceQueried() {
            }

            @Override
            public void onShutdownNotification() {
                if (fragmentLayout != null) {
                    fragmentLayout.setVisibility(View.GONE);
                }
                /*
                controller = setupPlaybackController();
                if (butPlay != null) {
                    butPlay.setOnClickListener(controller
                            .newOnPlayButtonClickListener());
                }
                */

            }

            @Override
            public void onPlaybackEnd() {
                if (fragmentLayout != null) {
                    fragmentLayout.setVisibility(View.GONE);
                }
                /*
                controller = setupPlaybackController();
                if (butPlay != null) {
                    butPlay.setOnClickListener(controller
                            .newOnPlayButtonClickListener());
                }
                */
            }

            @Override
            public void onPlaybackSpeedChange() {
                // TODO Auto-generated method stub

            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        controller.init();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (AppConfig.DEBUG)
            Log.d(TAG, "Fragment is about to be destroyed");
        if (controller != null) {
            controller.release();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (controller != null) {
            controller.pause();
        }
    }

    private boolean loadMediaInfo() {
        if (AppConfig.DEBUG)
            Log.d(TAG, "Loading media info");
        if (controller.serviceAvailable()) {
            Playable media = controller.getMedia();
            if (media != null) {
                txtvTitleExpanded.setText(media.getEpisodeTitle());
                txtvTitleAnchored.setText(media.getEpisodeTitle());
                ImageLoader.getInstance().loadThumbnailBitmap(
                        media,
                        imgvCoverExpanded,
                        (int) getActivity().getResources().getDimension(
                                R.dimen.external_player_height));
                ImageLoader.getInstance().loadThumbnailBitmap(
                        media,
                        imgvCoverAnchored,
                        (int) getActivity().getResources().getDimension(
                                R.dimen.external_player_height));

                txtvPositionExpanded.setText(Converter.getDurationStringLong(media.getPosition()));
                txtvLengthExpanded.setText(Converter.getDurationStringLong(media.getDuration()));
                try {
                    txtvDescriptionExpanded.setText(media.loadShownotes().call());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (media.getWebsiteLink() == null) {
                    butActionExpanded.setVisibility(View.INVISIBLE);
                } else {
                    butActionExpanded.setVisibility(View.VISIBLE);
                }

                fragmentLayout.setVisibility(View.VISIBLE);
                /*
                if (controller.isPlayingVideo()) {
                    butPlay.setVisibility(View.GONE);
                } else {
                    butPlay.setVisibility(View.VISIBLE);
                }
                */
                return true;
            } else {
                Log.w(TAG,
                        "loadMediaInfo was called while the media object of playbackService was null!");
                return false;
            }
        } else {
            Log.w(TAG,
                    "loadMediaInfo was called while playbackService was null!");
            return false;
        }
    }

    public View getExpandView() {
        return layoutInfoAnchored;
    }

    public View getCollapseView() {
        return layoutInfoExpanded;
    }
}
