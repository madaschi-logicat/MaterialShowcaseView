package uk.co.deanwild.materialshowcaseview;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedList;
import java.util.Queue;


public class MaterialShowcaseSequence implements IDetachedListener {

    PrefsManager mPrefsManager;
    Queue<MaterialShowcaseView> mShowcaseQueue;
    private boolean mSingleUse = false;
    Activity mActivity;
    ViewGroup mSupport;
    SupportProviderInterface mSupportProvider;
    private ShowcaseConfig mConfig;
    private int mSequencePosition = 0;
    private Runnable mSequenceFinishListener = null;

    private OnSequenceItemShownListener mOnItemShownListener = null;
    private OnSequenceItemDismissedListener mOnItemDismissedListener = null;

    public MaterialShowcaseSequence(Activity activity) {
        mActivity = activity;
        mShowcaseQueue = new LinkedList<>();
    }

    public MaterialShowcaseSequence(Activity activity, String sequenceID) {
        this(activity);
        this.singleUse(sequenceID);
    }

    public ViewGroup getSupport() {
        if (mSupportProvider != null) {
            return mSupportProvider.getSupport();
        }
        else if (mSupport == null) {
            if (mActivity == null) return null;
            return (ViewGroup) mActivity.getWindow().getDecorView();
        }
        return mSupport;
    }

    public void setSupport(ViewGroup support) {
        this.mSupport = support;
    }

    public void setSupport(SupportProviderInterface provider) {
        this.mSupportProvider = provider;
    }

    public MaterialShowcaseSequence addSequenceItem(View targetView, String content, String dismissText) {
        addSequenceItem(targetView, "", content, dismissText);
        return this;
    }

    public MaterialShowcaseSequence addSequenceItem(View targetView, String title, String content, String dismissText) {

        MaterialShowcaseView sequenceItem = new MaterialShowcaseView.Builder(mActivity)
                .setTarget(targetView)
                .setTitleText(title)
                .setDismissText(dismissText)
                .setContentText(content)
                .setSequence(true)
                .build();

        if (mConfig != null) {
            sequenceItem.setConfig(mConfig);
        }

        mShowcaseQueue.add(sequenceItem);
        return this;
    }

    public MaterialShowcaseSequence addSequenceItemRectangle(View targetView, String content, String dismissText) {
        addSequenceItemRectangle(targetView, "", content, dismissText);
        return this;
    }

    public MaterialShowcaseSequence addSequenceItemRectangle(View targetView, String title, String content, String dismissText) {

        MaterialShowcaseView sequenceItem = new MaterialShowcaseView.Builder(mActivity)
                .setTarget(targetView)
                .setTitleText(title)
                .setDismissText(dismissText)
                .setContentText(content)
                .withRectangleShape()
                .setSequence(true)
                .build();

        if (mConfig != null) {
            sequenceItem.setConfig(mConfig);
        }

        mShowcaseQueue.add(sequenceItem);
        return this;
    }

    public MaterialShowcaseSequence addSequenceItem(MaterialShowcaseView sequenceItem) {

        if (mConfig != null) {
            sequenceItem.setConfig(mConfig);
        }

        mShowcaseQueue.add(sequenceItem);
        return this;
    }

    public MaterialShowcaseSequence singleUse(String sequenceID) {
        mSingleUse = true;
        mPrefsManager = new PrefsManager(mActivity, sequenceID);
        return this;
    }

    public void setOnItemShownListener(OnSequenceItemShownListener listener) {
        this.mOnItemShownListener = listener;
    }

    public void setOnItemDismissedListener(OnSequenceItemDismissedListener listener) {
        this.mOnItemDismissedListener = listener;
    }

    public void setOnSequenceFinishListener(Runnable sequenceFinishListener) {
        this.mSequenceFinishListener = sequenceFinishListener;
    }

    public boolean hasFired() {

        if (mPrefsManager.getSequenceStatus() == PrefsManager.SEQUENCE_FINISHED) {
            return true;
        }

        return false;
    }

    public void setFired() {
        mPrefsManager.setFired();
    }

    public void start() {

        /**
         * Check if we've already shot our bolt and bail out if so         *
         */
        if (mSingleUse) {
            if (hasFired()) {
                fireFinishCallback();
                return;
            }

            /**
             * See if we have started this sequence before, if so then skip to the point we reached before
             * instead of showing the user everything from the start
             */
            mSequencePosition = mPrefsManager.getSequenceStatus();

            if (mSequencePosition > 0) {
                for (int i = 0; i < mSequencePosition; i++) {
                    mShowcaseQueue.poll();
                }
            }
        }


        // do start
        if (mShowcaseQueue.size() > 0)
            showNextItem();
    }

    private void showNextItem() {

        if (mShowcaseQueue.size() > 0 && !mActivity.isFinishing()) {
            MaterialShowcaseView sequenceItem = mShowcaseQueue.remove();
            sequenceItem.setDetachedListener(this);
            sequenceItem.show(getSupport());
            if (mOnItemShownListener != null) {
                mOnItemShownListener.onShow(sequenceItem, mSequencePosition);
            }
        } else {
            /**
             * We've reached the end of the sequence, save the fired state
             */
            if (mSingleUse) {
                mPrefsManager.setFired();
                fireFinishCallback();
            }
        }
    }

    private void skipTutorial() {

        mShowcaseQueue.clear();

        if (mShowcaseQueue.size() > 0 && !mActivity.isFinishing()) {
            MaterialShowcaseView sequenceItem = mShowcaseQueue.remove();
            sequenceItem.setDetachedListener(this);
            sequenceItem.show(getSupport());
            if (mOnItemShownListener != null) {
                mOnItemShownListener.onShow(sequenceItem, mSequencePosition);
            }
        } else {
            /**
             * We've reached the end of the sequence, save the fired state
             */
            if (mSingleUse) {
                mPrefsManager.setFired();
                fireFinishCallback();
            }
        }
    }

    private void fireFinishCallback() {
        if (mSequenceFinishListener != null) mSequenceFinishListener.run();
    }

    @Override
    public void onShowcaseDetached(MaterialShowcaseView showcaseView, boolean wasDismissed, boolean wasSkipped) {

        showcaseView.setDetachedListener(null);

        /**
         * We're only interested if the showcase was purposefully dismissed
         */
        if (wasDismissed) {

            if (mOnItemDismissedListener != null) {
                mOnItemDismissedListener.onDismiss(showcaseView, mSequencePosition);
            }

            /**
             * If so, update the prefsManager so we can potentially resume this sequence in the future
             */
            if (mPrefsManager != null) {
                mSequencePosition++;
                mPrefsManager.setSequenceStatus(mSequencePosition);
            }

            showNextItem();
        }

        if(wasSkipped){
            if (mOnItemDismissedListener != null) {
                mOnItemDismissedListener.onDismiss(showcaseView, mSequencePosition);
            }

            /**
             * If so, update the prefsManager so we can potentially resume this sequence in the future
             */
            if (mPrefsManager != null) {
                mSequencePosition++;
                mPrefsManager.setSequenceStatus(mSequencePosition);
            }

            skipTutorial();
        }
    }

    public void setConfig(ShowcaseConfig config) {
        this.mConfig = config;
    }

    public interface OnSequenceItemShownListener {
        void onShow(MaterialShowcaseView itemView, int position);
    }

    public interface OnSequenceItemDismissedListener {
        void onDismiss(MaterialShowcaseView itemView, int position);
    }

    public interface SupportProviderInterface {
        ViewGroup getSupport();
    }

}
