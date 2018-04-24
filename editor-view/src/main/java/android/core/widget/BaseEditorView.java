/*
 *
 *  * Copyright (C) 2006 The Android Open Source Project
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package android.core.widget;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.core.content.UndoManager;
import android.core.text.BoringLayout;
import android.core.text.DynamicLayout;
import android.core.text.Layout;
import android.core.text.LayoutContext;
import android.core.text.MetaKeyKeyListenerCompat;
import android.core.text.Selection;
import android.core.text.SpannableStringBuilder;
import android.core.text.StaticLayout;
import android.core.text.TextDirectionHeuristic;
import android.core.text.TextDirectionHeuristics;
import android.core.text.TextLineNumber;
import android.core.text.TextUtils;
import android.core.text.method.ArrowKeyMovementMethod;
import android.core.text.method.LinkMovementMethod;
import android.core.text.method.MovementMethod;
import android.core.text.method.TransformationMethod2;
import android.core.text.method.WordIterator;
import android.core.util.FastMath;
import android.core.view.InputMethodManagerCompat;
import android.core.view.ViewCompat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.ParcelableSpan;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.DateKeyListener;
import android.text.method.DateTimeKeyListener;
import android.text.method.DialerKeyListener;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.MetaKeyKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TextKeyListener;
import android.text.method.TimeKeyListener;
import android.text.method.TransformationMethod;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ParagraphStyle;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;
import android.text.style.UpdateAppearance;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Choreographer;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.RemoteViews.RemoteView;
import android.widget.Scroller;

import com.duy.text.editor.R;
import com.jecelyin.common.utils.DLog;
import com.jecelyin.common.utils.SysUtils;
import com.jecelyin.editor.v2.Pref;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Displays text to the user and optionally allows them to edit it.  A TextView
 * is a complete text editor, however the basic class is configured to not
 * allow editing; see {@link android.widget.EditText} for a subclass that configures the text
 * view for editing.
 * <p>
 * <p>
 * To allow users to copy some or all of the TextView's value and paste it somewhere else, set the
 * XML attribute {link android.R.styleable#TextView_textIsSelectable
 * android:textIsSelectable} to "true" or call
 * {@link #setTextIsSelectable setTextIsSelectable(true)}. The {@code textIsSelectable} flag
 * allows users to make selection gestures in the TextView, which in turn triggers the system's
 * built-in copy/paste controls.
 * <p>
 * <b>XML attributes</b>
 * <p>
 * See {link android.R.styleable#TextView TextView Attributes},
 * {link android.R.styleable#View View Attributes}
 */
@RemoteView
public class BaseEditorView extends View implements ViewTreeObserver.OnPreDrawListener, SharedPreferences.OnSharedPreferenceChangeListener {
    static final String LOG_TAG = "TextView";
    static final boolean DEBUG_EXTRACT = false;
    static final int ID_SELECT_ALL = android.R.id.selectAll;
    static final int ID_CUT = android.R.id.cut;
    static final int ID_COPY = android.R.id.copy;
    static final int ID_PASTE = android.R.id.paste;
    // Enum for the "typeface" XML parameter.
    // TODO: How can we get this from the XML instead of hardcoding it here?
    private static final int SANS = 1;
    private static final int SERIF = 2;
    private static final int MONOSPACE = 3;
    // Bitfield for the "numeric" XML parameter.
    // TODO: How can we get this from the XML instead of hardcoding it here?
    private static final int SIGNED = 2;
    private static final int DECIMAL = 4;
    /**
     * Draw marquee text with fading edges as usual
     */
    private static final int MARQUEE_FADE_NORMAL = 0;
    /**
     * Draw marquee text as ellipsize end while inactive instead of with the fade.
     * (Useful for devices where the fade can be expensive if overdone)
     */
    private static final int MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS = 1;
    /**
     * Draw marquee text with fading edges because it is currently active/animating.p
     */
    private static final int MARQUEE_FADE_SWITCH_SHOW_FADE = 2;
    private static final int LINES = 1;
    private static final int EMS = LINES;
    private static final int PIXELS = 2;
    private static final RectF TEMP_RECTF = new RectF();
    // XXX should be much larger
    private static final int VERY_WIDE = 1024 * 1024;
    private static final int ANIMATED_SCROLL_GAP = 250;
    private static final InputFilter[] NO_FILTERS = new InputFilter[0];
    private static final Spanned EMPTY_SPANNED = new SpannedString("");
    private static final int CHANGE_WATCHER_PRIORITY = 100;
    private static final BoringLayout.Metrics UNKNOWN_BORING = new BoringLayout.Metrics();
    private static final String TAG = "BaseEditorView";
    // System wide time for last cut or copy action.
    static long LAST_CUT_OR_COPY_TIME;

    /*
     * Kick-start the font cache for the zygote process (to pay the cost of
     * initializing freetype for our default font only once).
     */
    static {
        Paint p = new Paint();
        p.setAntiAlias(true);
        // We don't care about the result, just the side-effect of measuring.
        p.measureText("H");
    }

    // display attributes
    private final TextPaint mTextPaint;
    private final Paint mHighlightPaint;
    /**
     * EditText specific data, created on demand when one of the Editor fields is used.
     * See {@link #createEditorIfNeeded()}.
     */
    protected Editor mEditor;
    protected Pref pref;
    // It is possible to have a selection even when mEditor is null (programmatically set, like when
    // a link is pressed). These highlight-related fields do not go in mEditor.
    int mHighlightColor = 0x6633B5E5;
    // Although these fields are specific to editable text, they are not added to Editor because
    // they are defined by the TextView's style and are theme-dependent.
    int mCursorDrawableRes;
    // These four fields, could be moved to Editor, since we know their default values and we
    // could condition the creation of the Editor to a non standard value. This is however
    // brittle since the hardcoded values here (such as
    // android.R.drawable.text_select_handle_left) would have to be updated if the
    // default style is modified.
    int mTextSelectHandleLeftRes;
    int mTextSelectHandleRightRes;
    int mTextSelectHandleRes;
    int mTextEditSuggestionItemLayout;
    private ColorStateList mTextColor;
    private ColorStateList mHintTextColor;
    private ColorStateList mLinkTextColor;
    private int mCurTextColor;
    private int mCurHintTextColor;
    private boolean mFreezesText;
    private boolean mTemporaryDetach;
    private boolean mDispatchTemporaryDetach;
    //    private Editable.Factory mEditableFactory = Editable.Factory.getInstance();
    private Spannable.Factory mSpannableFactory = Spannable.Factory.getInstance();
    private float mShadowRadius, mShadowDx, mShadowDy;
    private int mShadowColor;
    private boolean mPreDrawRegistered;
    private boolean mPreDrawListenerDetached;
    // A flag to prevent repeated movements from escaping the enclosing text view. The idea here is
    // that if a user is holding down a movement key to traverse text, we shouldn't also traverse
    // the view hierarchy. On the other hand, if the user is using the movement key to traverse views
    // (i.e. the first movement was to traverse out of this view, or this view was traversed into by
    // the user holding the movement key down) then we shouldn't prevent the focus from changing.
    private boolean mPreventDefaultMovement;
    private int mLastCursorOffset = -1;
    private Marquee mMarquee;
    private boolean mRestartMarquee;
    private int mMarqueeRepeatLimit = 3;
    private int mLastLayoutDirection = -1;
    /**
     * On some devices the fading edges add a performance penalty if used
     * extensively in the same layout. This mode indicates how the marquee
     * is currently being shown, if applicable. (mEllipsize will == MARQUEE)
     */
    private int mMarqueeFadeMode = MARQUEE_FADE_NORMAL;
    /**
     * When mMarqueeFadeMode is not MARQUEE_FADE_NORMAL, this stores
     * the layout that should be used when the mode switches.
     */
    private Layout mSavedMarqueeModeLayout;
    private CharSequence mText;
    private CharSequence mTransformed;
    private BufferType mBufferType = BufferType.NORMAL;
    private CharSequence mHint;
    private Layout mHintLayout;
    private MovementMethod mMovement;
    private TransformationMethod mTransformation;
    private boolean mAllowTransformationLengthChange;
    private ChangeWatcher mChangeWatcher;
    private ArrayList<TextWatcher> mListeners;
    private boolean mUserSetTextScaleX;
    private Layout mLayout;
    private int mGravity = Gravity.TOP | Gravity.START;
    private boolean mHorizontallyScrolling;
    private int mAutoLinkMask;
    private boolean mLinksClickable = true;
    private float mSpacingMult = 1.0f;
    private float mSpacingAdd = 0.0f;
    private int mMaximum = Integer.MAX_VALUE;
    private int mMaxMode = LINES;
    private int mMinimum = 0;
    private int mMinMode = LINES;
    private int mOldMaximum = mMaximum;
    private int mOldMaxMode = mMaxMode;
    private int mMaxWidth = Integer.MAX_VALUE;
    private int mMaxWidthMode = PIXELS;
    private int mMinWidth = 0;
    private int mMinWidthMode = PIXELS;
    private boolean mSingleLine;
    private int mDesiredHeightAtMeasure = -1;
    private boolean mIncludePad = true;
    private int mDeferScroll = -1;
    // tmp primitives, so we don't alloc them on each draw
    private Rect mTempRect;
    private long mLastScroll;
    private Scroller mScroller;
    private BoringLayout.Metrics mBoring, mHintBoring;
    private BoringLayout mSavedLayout, mSavedHintLayout;
    private TextDirectionHeuristic mTextDir;
    private InputFilter[] mFilters = NO_FILTERS;
    private volatile Locale mCurrentSpellCheckerLocaleCache;
    private Path mHighlightPath;
    private boolean mHighlightPathBogus = true;
    private LayoutContext layoutContext = new LayoutContext();

    public BaseEditorView(Context context) {
        this(context, null);
    }

    public BaseEditorView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public BaseEditorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        int defStyleRes = 0; //jec+

        mText = "";

        final Resources res = getResources();

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.density = res.getDisplayMetrics().density;

        mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mMovement = getDefaultMovementMethod();

        mTransformation = null;

        int textColorHighlight = 0;
//        ColorStateList textColor = null; //jec-
        ColorStateList textColor = ColorStateList.valueOf(Color.parseColor("#333333"));
        ColorStateList textColorHint = ColorStateList.valueOf(Color.parseColor("#808080"));
        ColorStateList textColorLink = null;
        int textSize = getResources().getDimensionPixelSize(R.dimen.editor_text_size);
        String fontFamily = null;
        int typefaceIndex = MONOSPACE;
        int styleIndex = Typeface.NORMAL;
        boolean allCaps = false;
        int shadowcolor = 0;
        float dx = 0, dy = 0, r = 0;
        boolean elegant = false;
        float letterSpacing = 0;
        String fontFeatureSettings = null;

        boolean editable = getDefaultEditable();
        CharSequence inputMethod = null;
        int numeric = 0;
        CharSequence digits = null;
        boolean phone = false;
        boolean autotext = false;
        int autocap = -1;
        int buffertype = 0;
        boolean selectallonfocus = false;
        Drawable drawableLeft = null, drawableTop = null, drawableRight = null,
                drawableBottom = null, drawableStart = null, drawableEnd = null;
        int drawablePadding = 0;
        int ellipsize = -1;
        boolean singleLine = false;
        int maxlength = -1;
        CharSequence text = "";
        CharSequence hint = null;
        boolean password = false;
        int inputType = EditorInfo.TYPE_NULL;

        setGravity(Gravity.START | Gravity.TOP);
        setTextScaleX(1.0f);
        setEnabled(isEnabled());
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mTextSelectHandleLeftRes = R.drawable.text_select_handle_left_mtrl_alpha;
        mTextSelectHandleRightRes = R.drawable.text_select_handle_right_mtrl_alpha;
        mTextSelectHandleRes = R.drawable.text_select_handle_middle_mtrl_alpha;
        mTextEditSuggestionItemLayout = R.layout.text_edit_suggestion_item;

        BufferType bufferType = BufferType.EDITABLE;

        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        final boolean passwordInputType = variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        final boolean webPasswordInputType = variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        final boolean numberPasswordInputType = variation
                == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);

        if (inputMethod != null) {
            Class<?> c;

            try {
                c = Class.forName(inputMethod.toString());
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }

            try {
                createEditorIfNeeded();
                mEditor.mKeyListener = (KeyListener) c.newInstance();
            } catch (InstantiationException ex) {
                throw new RuntimeException(ex);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
            try {
                mEditor.mInputType = inputType != EditorInfo.TYPE_NULL
                        ? inputType
                        : mEditor.mKeyListener.getInputType();
            } catch (IncompatibleClassChangeError e) {
                mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
            }
        } else if (digits != null) {
            createEditorIfNeeded();
            mEditor.mKeyListener = DigitsKeyListener.getInstance(digits.toString());
            // If no input type was specified, we will default to generic
            // text, since we can't tell the IME about the set of digits
            // that was selected.
            mEditor.mInputType = inputType != EditorInfo.TYPE_NULL
                    ? inputType : EditorInfo.TYPE_CLASS_TEXT;
        } else if (inputType != EditorInfo.TYPE_NULL) {
            setInputType(inputType, true);
            // If set, the input type overrides what was set using the deprecated singleLine flag.
            singleLine = !isMultilineInputType(inputType);
        } else if (phone) {
            createEditorIfNeeded();
            mEditor.mKeyListener = DialerKeyListener.getInstance();
            mEditor.mInputType = inputType = EditorInfo.TYPE_CLASS_PHONE;
        } else if (numeric != 0) {
            createEditorIfNeeded();
            mEditor.mKeyListener = DigitsKeyListener.getInstance((numeric & SIGNED) != 0,
                    (numeric & DECIMAL) != 0);
            inputType = EditorInfo.TYPE_CLASS_NUMBER;
            if ((numeric & SIGNED) != 0) {
                inputType |= EditorInfo.TYPE_NUMBER_FLAG_SIGNED;
            }
            if ((numeric & DECIMAL) != 0) {
                inputType |= EditorInfo.TYPE_NUMBER_FLAG_DECIMAL;
            }
            mEditor.mInputType = inputType;
        } else if (autotext || autocap != -1) {
            TextKeyListener.Capitalize cap;

            inputType = EditorInfo.TYPE_CLASS_TEXT;

            switch (autocap) {
                case 1:
                    cap = TextKeyListener.Capitalize.SENTENCES;
                    inputType |= EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES;
                    break;

                case 2:
                    cap = TextKeyListener.Capitalize.WORDS;
                    inputType |= EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;
                    break;

                case 3:
                    cap = TextKeyListener.Capitalize.CHARACTERS;
                    inputType |= EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS;
                    break;

                default:
                    cap = TextKeyListener.Capitalize.NONE;
                    break;
            }

            createEditorIfNeeded();
            mEditor.mKeyListener = TextKeyListener.getInstance(autotext, cap);
            mEditor.mInputType = inputType;
        } else if (isTextSelectable()) {
            // Prevent text changes from keyboard.
            if (mEditor != null) {
                mEditor.mKeyListener = null;
                mEditor.mInputType = EditorInfo.TYPE_NULL;
            }
            bufferType = BufferType.SPANNABLE;
            // So that selection can be changed using arrow keys and touch is handled.
            setMovementMethod(ArrowKeyMovementMethod.getInstance());
        } else if (editable) {
            createEditorIfNeeded();
            mEditor.mKeyListener = TextKeyListener.getInstance();
            mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
        } else {
            if (mEditor != null) mEditor.mKeyListener = null;

            switch (buffertype) {
                case 0:
                    bufferType = BufferType.NORMAL;
                    break;
                case 1:
                    bufferType = BufferType.SPANNABLE;
                    break;
                case 2:
                    bufferType = BufferType.EDITABLE;
                    break;
            }
        }

        if (mEditor != null) mEditor.adjustInputType(password, passwordInputType,
                webPasswordInputType, numberPasswordInputType);

        if (selectallonfocus) {
            createEditorIfNeeded();
            mEditor.mSelectAllOnFocus = true;

            if (bufferType == BufferType.NORMAL)
                bufferType = BufferType.SPANNABLE;
        }

        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, singleLine, singleLine);

        if (singleLine && getKeyListener() == null && ellipsize < 0) {
            ellipsize = 3; // END
        }


        setTextColor(textColor != null ? textColor : ColorStateList.valueOf(0xFF000000));
        setHintTextColor(textColorHint);
        setLinkTextColor(textColorLink);
        if (textColorHighlight != 0) {
            setHighlightColor(textColorHighlight);
        }
        setRawTextSize(textSize);

        if (password || passwordInputType || webPasswordInputType || numberPasswordInputType) {
            setTransformationMethod(PasswordTransformationMethod.getInstance());
            typefaceIndex = MONOSPACE;
        } else if (mEditor != null &&
                (mEditor.mInputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION))
                        == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)) {
            typefaceIndex = MONOSPACE;
        }

        setTypefaceFromAttrs(fontFamily, typefaceIndex, styleIndex);

        if (shadowcolor != 0) {
            setShadowLayer(r, dx, dy, shadowcolor);
        }

        if (maxlength >= 0) {
            setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxlength)});
        } else {
            setFilters(NO_FILTERS);
        }

        setText(text, bufferType);
        if (hint != null) setHint(hint);

        /*
         * Views are not normally focusable unless specified to be.
         * However, TextViews that have input or movement methods *are*
         * focusable by default.
         */

        boolean focusable = mMovement != null || getKeyListener() != null;
        boolean clickable = focusable || isClickable();
        boolean longClickable = focusable || isLongClickable();


        setFocusable(focusable);
        setClickable(clickable);
        setLongClickable(longClickable);

        if (mEditor != null) mEditor.prepareCursorControllers();

        // If not explicitly specified this view is important for accessibility.
        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        init();
    }

    private static boolean isMultilineInputType(int type) {
        return (type & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE)) ==
                (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    }

    private static boolean isPasswordInputType(int inputType) {
        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        return variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)
                || variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || variation
                == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private static boolean isVisiblePasswordInputType(int inputType) {
        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        return variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    }

    /**
     * This is used to remove all style-impacting spans from text before new
     * extracted text is being replaced into it, so that we don't have any
     * lingering spans applied during the replace.
     */
    static void removeParcelableSpans(Spannable spannable, int start, int end) {
        Object[] spans = spannable.getSpans(start, end, ParcelableSpan.class);
        int i = spans.length;
        while (i > 0) {
            i--;
            spannable.removeSpan(spans[i]);
        }
    }

    private static int desired(Layout layout) {
        int n = layout.getLineCount();
        CharSequence text = layout.getText();
        float max = 0;

        // if any line was wrapped, we can't use it.
        // but it's ok for the last line not to have a newline

        for (int i = 0; i < n - 1; i++) {
            if (text.charAt(layout.getLineEnd(i) - 1) != '\n')
                return -1;
        }

        for (int i = 0; i < n; i++) {
            max = Math.max(max, layout.getLineWidth(i));
        }

        return (int) Math.ceil(max);
    }

    /**
     * Returns the TextView_textColor attribute from the TypedArray, if set, or
     * the TextAppearance_textColor from the TextView_textAppearance attribute,
     * if TextView_textColor was not set directly.
     *
     * @removed
     */
    public static ColorStateList getTextColors(Context context, TypedArray attrs) {
        if (attrs == null) {
            // Preserve behavior prior to removal of this API.
            throw new NullPointerException();
        }

        // It's not safe to use this method from apps. The parameter 'attrs'
        // must have been obtained using the TextView filter array which is not
        // available to the SDK. As such, we grab a default TypedArray with the
        // right filter instead here.
//        final TypedArray a = context.obtainStyledAttributes(R.styleable.TextView);
//        ColorStateList colors = a.getColorStateList(R.styleable.TextView_textColor);
//        if (colors == null) {
//            final int ap = a.getResourceId(R.styleable.TextView_textAppearance, 0);
//            if (ap != 0) {
//                final TypedArray appearance = context.obtainStyledAttributes(
//                        ap, R.styleable.TextAppearance);
//                colors = appearance.getColorStateList(R.styleable.TextAppearance_textColor);
//                appearance.recycle();
//            }
//        }
//        a.recycle();
//
//        return colors;
        return null;
    }

    /**
     * Returns the default color from the TextView_textColor attribute from the
     * AttributeSet, if set, or the default color from the
     * TextAppearance_textColor from the TextView_textAppearance attribute, if
     * TextView_textColor was not set directly.
     *
     * @removed
     */
    public static int getTextColor(Context context, TypedArray attrs, int def) {
        final ColorStateList colors = getTextColors(context, attrs);
        if (colors == null) {
            return def;
        } else {
            return colors.getDefaultColor();
        }
    }

    private void setTypefaceFromAttrs(String familyName, int typefaceIndex, int styleIndex) {
        Typeface tf = null;
        if (familyName != null) {
            tf = Typeface.create(familyName, styleIndex);
            if (tf != null) {
                setTypeface(tf);
                return;
            }
        }
        switch (typefaceIndex) {
            case SANS:
                tf = Typeface.SANS_SERIF;
                break;

            case SERIF:
                tf = Typeface.SERIF;
                break;

            case MONOSPACE:
                tf = Typeface.MONOSPACE;
                break;
        }

        setTypeface(tf, styleIndex);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled == isEnabled()) {
            return;
        }

        if (!enabled) {
            // Hide the soft input if the currently active TextView is disabled
            InputMethodManager imm = InputMethodManagerCompat.peekInstance();
            if (imm != null && imm.isActive(this)) {
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        }

        super.setEnabled(enabled);

        if (enabled) {
            // Make sure IME is updated with current editor info.
            InputMethodManager imm = InputMethodManagerCompat.peekInstance();
            if (imm != null) imm.restartInput(this);
        }

        // Will change text color
        if (mEditor != null) {
            mEditor.invalidateTextDisplayList();
            mEditor.prepareCursorControllers();

            // start or stop the cursor blinking as appropriate
            mEditor.makeBlink();
        }
    }

    /**
     * Sets the typeface and style in which the text should be displayed,
     * and turns on the fake bold and italic bits in the Paint if the
     * Typeface that you provided does not have all the bits in the
     * style that you specified.
     *
     * @attr ref android.R.styleable#TextView_typeface
     * @attr ref android.R.styleable#TextView_textStyle
     */
    public void setTypeface(Typeface tf, int style) {
        if (style > 0) {
            if (tf == null) {
                tf = Typeface.defaultFromStyle(style);
            } else {
                tf = Typeface.create(tf, style);
            }

            setTypeface(tf);
            // now compute what (if any) algorithmic styling is needed
            int typefaceStyle = tf != null ? tf.getStyle() : 0;
            int need = style & ~typefaceStyle;
            mTextPaint.setFakeBoldText((need & Typeface.BOLD) != 0);
            mTextPaint.setTextSkewX((need & Typeface.ITALIC) != 0 ? -0.25f : 0);
        } else {
            mTextPaint.setFakeBoldText(false);
            mTextPaint.setTextSkewX(0);
            setTypeface(tf);
        }
    }

    /**
     * Subclasses override this to specify that they have a KeyListener
     * by default even if not specifically called for in the XML options.
     */
    protected boolean getDefaultEditable() {
        return false;
    }

    /**
     * Subclasses override this to specify a default movement method.
     */
    protected MovementMethod getDefaultMovementMethod() {
        return null;
    }

    /**
     * Return the text the TextView is displaying. If setText() was called with
     * an argument of BufferType.SPANNABLE or BufferType.EDITABLE, you can cast
     * the return value from this method to Spannable or Editable, respectively.
     * <p>
     * Note: The content of the return value should not be modified. If you want
     * a modifiable one, you should make your own copy first.
     *
     * @attr ref android.R.styleable#TextView_text
     */
    public CharSequence getText() {
        return mText;
    }

    /**
     * Sets the string value of the TextView. TextView <em>does not</em> accept
     * HTML-like formatting, which you can do with text strings in XML resource files.
     * To style your strings, attach android.text.style.* objects to a
     * {@link SpannableString SpannableString}, or see the
     * <a href="{@docRoot}guide/topics/resources/available-resources.html#stringresources">
     * Available Resource Types</a> documentation for an example of setting
     * formatted text in the XML resource file.
     *
     * @attr ref android.R.styleable#TextView_text
     */
    public final void setText(CharSequence text) {
        setText(text, mBufferType);
    }

    public final void setText(int resid) {
        setText(getContext().getResources().getText(resid));
    }

    /**
     * Returns the length, in characters, of the text managed by this TextView
     */
    public int length() {
        return mText.length();
    }

    /**
     * Return the text the TextView is displaying as an Editable object.  If
     * the text is not editable, null is returned.
     *
     * @see #getText
     */
    public Editable getEditableText() {
        return (mText instanceof Editable) ? (Editable) mText : null;
    }

    /**
     * @return the height of one standard line in pixels.  Note that markup
     * within the text can cause individual lines to be taller or shorter
     * than this height, and the layout may contain additional first-
     * or last-line padding.
     */
    public int getLineHeight() {
        return FastMath.round(mTextPaint.getFontMetricsInt(null) * mSpacingMult + mSpacingAdd);
    }

    /**
     * @return the Layout that is currently being used to display the text.
     * This can be null if the text or width has recently changes.
     */
    public final Layout getLayout() {
        return mLayout;
    }

    /**
     * @return the Layout that is currently being used to display the hint text.
     * This can be null.
     */
    final Layout getHintLayout() {
        return mHintLayout;
    }

    /**
     * Retrieve the {@link .content.UndoManager} that is currently associated
     * with this TextView.  By default there is no associated UndoManager, so null
     * is returned.  One can be associated with the TextView through
     * {@link #setUndoManager(UndoManager, String)}
     *
     * @hide
     */
    public final UndoManager getUndoManager() {
        return mEditor == null ? null : mEditor.mUndoManager;
    }

    /**
     * Associate an {@link .content.UndoManager} with this TextView.  Once
     * done, all edit operations on the TextView will result in appropriate
     * {@link .content.UndoOperation} objects pushed on the given UndoManager's
     * stack.
     *
     * @param undoManager The {@link .content.UndoManager} to associate with
     *                    this TextView, or null to clear any existing association.
     * @param tag         String tag identifying this particular TextView owner in the
     *                    UndoManager.  This is used to keep the correct association with the
     *                    {@link .content.UndoOwner} of any operations inside of the UndoManager.
     * @hide
     */
    public final void setUndoManager(UndoManager undoManager, String tag) {
        if (undoManager != null) {
            createEditorIfNeeded();
            mEditor.mUndoManager = undoManager;
            mEditor.mUndoOwner = undoManager.getOwner(tag, this);
            mEditor.mUndoInputFilter = new Editor.UndoInputFilter(mEditor);
            if (!(mText instanceof Editable)) {
                setText(mText, BufferType.EDITABLE);
            }

            setFilters((Editable) mText, mFilters);
        } else if (mEditor != null) {
            // XXX need to destroy all associated state.
            mEditor.mUndoManager = null;
            mEditor.mUndoOwner = null;
            mEditor.mUndoInputFilter = null;
        }
    }

    /**
     * @return the current key listener for this TextView.
     * This will frequently be null for non-EditText TextViews.
     * @attr ref android.R.styleable#TextView_numeric
     * @attr ref android.R.styleable#TextView_digits
     * @attr ref android.R.styleable#TextView_phoneNumber
     * @attr ref android.R.styleable#TextView_inputMethod
     * @attr ref android.R.styleable#TextView_capitalize
     * @attr ref android.R.styleable#TextView_autoText
     */
    public final KeyListener getKeyListener() {
        return mEditor == null ? null : mEditor.mKeyListener;
    }

    /**
     * Sets the key listener to be used with this TextView.  This can be null
     * to disallow user input.  Note that this method has significant and
     * subtle interactions with soft keyboards and other input method:
     * see {@link KeyListener#getInputType() KeyListener.getContentType()}
     * for important details.  Calling this method will replace the current
     * content type of the text view with the content type returned by the
     * key listener.
     * <p>
     * Be warned that if you want a TextView with a key listener or movement
     * method not to be focusable, or if you want a TextView without a
     * key listener or movement method to be focusable, you must call
     * {@link #setFocusable} again after calling this to get the focusability
     * back the way you want it.
     *
     * @attr ref android.R.styleable#TextView_numeric
     * @attr ref android.R.styleable#TextView_digits
     * @attr ref android.R.styleable#TextView_phoneNumber
     * @attr ref android.R.styleable#TextView_inputMethod
     * @attr ref android.R.styleable#TextView_capitalize
     * @attr ref android.R.styleable#TextView_autoText
     */
    public void setKeyListener(KeyListener input) {
        setKeyListenerOnly(input);
        fixFocusableAndClickableSettings();

        if (input != null) {
            createEditorIfNeeded();
            try {
                mEditor.mInputType = mEditor.mKeyListener.getInputType();
            } catch (IncompatibleClassChangeError e) {
                mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
            }
            // Change inputType, without affecting transformation.
            // No need to applySingleLine since mSingleLine is unchanged.
            setInputTypeSingleLine(mSingleLine);
        } else {
            if (mEditor != null) mEditor.mInputType = EditorInfo.TYPE_NULL;
        }

        InputMethodManager imm = InputMethodManagerCompat.peekInstance();
        if (imm != null) imm.restartInput(this);
    }

    private void setKeyListenerOnly(KeyListener input) {
        if (mEditor == null && input == null) return; // null is the default value

        createEditorIfNeeded();
        if (mEditor.mKeyListener != input) {
            mEditor.mKeyListener = input;
            if (input != null && !(mText instanceof Editable)) {
                setText(mText);
            }

            setFilters((Editable) mText, mFilters);
        }
    }

    /**
     * @return the movement method being used for this TextView.
     * This will frequently be null for non-EditText TextViews.
     */
    public final MovementMethod getMovementMethod() {
        return mMovement;
    }

    /**
     * Sets the movement method (arrow key handler) to be used for
     * this TextView.  This can be null to disallow using the arrow keys
     * to move the cursor or scroll the view.
     * <p>
     * Be warned that if you want a TextView with a key listener or movement
     * method not to be focusable, or if you want a TextView without a
     * key listener or movement method to be focusable, you must call
     * {@link #setFocusable} again after calling this to get the focusability
     * back the way you want it.
     */
    public final void setMovementMethod(MovementMethod movement) {
        if (mMovement != movement) {
            mMovement = movement;

            if (movement != null && !(mText instanceof Spannable)) {
                setText(mText);
            }

            fixFocusableAndClickableSettings();

            // SelectionModifierCursorController depends on textCanBeSelected, which depends on
            // mMovement
            if (mEditor != null) mEditor.prepareCursorControllers();
        }
    }

    private void fixFocusableAndClickableSettings() {
        if (mMovement != null || (mEditor != null && mEditor.mKeyListener != null)) {
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
        } else {
            setFocusable(false);
            setClickable(false);
            setLongClickable(false);
        }
    }

    /**
     * @return the current transformation method for this TextView.
     * This will frequently be null except for single-line and password
     * fields.
     * @attr ref android.R.styleable#TextView_password
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public final TransformationMethod getTransformationMethod() {
        return mTransformation;
    }

    /**
     * Sets the transformation that is applied to the text that this
     * TextView is displaying.
     *
     * @attr ref android.R.styleable#TextView_password
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public final void setTransformationMethod(TransformationMethod method) {
        if (method == mTransformation) {
            // Avoid the setText() below if the transformation is
            // the same.
            return;
        }
        if (mTransformation != null) {
            if (mText instanceof Spannable) {
                ((Spannable) mText).removeSpan(mTransformation);
            }
        }

        mTransformation = method;

        if (method instanceof TransformationMethod2) {
            TransformationMethod2 method2 = (TransformationMethod2) method;
            mAllowTransformationLengthChange = !isTextSelectable() && !(mText instanceof Editable);
            method2.setLengthChangesAllowed(mAllowTransformationLengthChange);
        } else {
            mAllowTransformationLengthChange = false;
        }

        setText(mText);

//        if (hasPasswordTransformationMethod()) {
//            notifyViewAccessibilityStateChangedIfNeeded(
//                    AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
//        }
    }

    /**
     * Returns the top padding of the view, plus space for the top
     * Drawable if any.
     */
    public int getCompoundPaddingTop() {
        return getPaddingTop();
    }

    /**
     * Returns the bottom padding of the view, plus space for the bottom
     * Drawable if any.
     */
    public int getCompoundPaddingBottom() {
        return getPaddingBottom();
    }

    /**
     * Returns the left padding of the view, plus space for the left
     * Drawable if any.
     */
    public int getCompoundPaddingLeft() {
        return getPaddingStart();
    }

    /**
     * Returns the right padding of the view, plus space for the right
     * Drawable if any.
     */
    public int getCompoundPaddingRight() {
        return getPaddingEnd();
    }

    /**
     * Returns the start padding of the view, plus space for the start
     * Drawable if any.
     */
    public int getCompoundPaddingStart() {
//        resolveDrawables();
        switch (getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingLeft();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingRight();
        }
    }

    /**
     * Returns the end padding of the view, plus space for the end
     * Drawable if any.
     */
    public int getCompoundPaddingEnd() {
//        resolveDrawables();
        switch (getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingRight();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingLeft();
        }
    }

    /**
     * Returns the extended top padding of the view, including both the
     * top Drawable if any and any extra space to keep more than maxLines
     * of text from showing.  It is only valid to call this after measuring.
     */
    public int getExtendedPaddingTop() {
        if (mMaxMode != LINES) {
            return getCompoundPaddingTop();
        }

        if (mLayout == null) {
            assumeLayout();
        }

        if (mLayout.getLineCount() <= mMaximum) {
            return getCompoundPaddingTop();
        }

        int top = getCompoundPaddingTop();
        int bottom = getCompoundPaddingBottom();
        int viewht = getHeight() - top - bottom;
        int layoutht = mLayout.getLineTop(mMaximum);

        if (layoutht >= viewht) {
            return top;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return top;
        } else if (gravity == Gravity.BOTTOM) {
            return top + viewht - layoutht;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return top + (viewht - layoutht) / 2;
        }
    }

    /**
     * Returns the extended bottom padding of the view, including both the
     * bottom Drawable if any and any extra space to keep more than maxLines
     * of text from showing.  It is only valid to call this after measuring.
     */
    public int getExtendedPaddingBottom() {
        if (mMaxMode != LINES) {
            return getCompoundPaddingBottom();
        }

        if (mLayout == null) {
            assumeLayout();
        }

        if (mLayout.getLineCount() <= mMaximum) {
            return getCompoundPaddingBottom();
        }

        int top = getCompoundPaddingTop();
        int bottom = getCompoundPaddingBottom();
        int viewht = getHeight() - top - bottom;
        int layoutht = mLayout.getLineTop(mMaximum);

        if (layoutht >= viewht) {
            return bottom;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return bottom + viewht - layoutht;
        } else if (gravity == Gravity.BOTTOM) {
            return bottom;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return bottom + (viewht - layoutht) / 2;
        }
    }

    /**
     * Returns the total left padding of the view, including the left
     * Drawable if any.
     */
    public int getTotalPaddingLeft() {
        return getCompoundPaddingLeft();
    }

    /**
     * Returns the total right padding of the view, including the right
     * Drawable if any.
     */
    public int getTotalPaddingRight() {
        return getCompoundPaddingRight();
    }

    /**
     * Returns the total start padding of the view, including the start
     * Drawable if any.
     */
    public int getTotalPaddingStart() {
        return getCompoundPaddingStart();
    }

    /**
     * Returns the total end padding of the view, including the end
     * Drawable if any.
     */
    public int getTotalPaddingEnd() {
        return getCompoundPaddingEnd();
    }

    /**
     * Returns the total top padding of the view, including the top
     * Drawable if any, the extra space to keep more than maxLines
     * from showing, and the vertical offset for gravity, if any.
     */
    public int getTotalPaddingTop() {
        return getExtendedPaddingTop() + getVerticalOffset(true);
    }

    /**
     * Returns the total bottom padding of the view, including the bottom
     * Drawable if any, the extra space to keep more than maxLines
     * from showing, and the vertical offset for gravity, if any.
     */
    public int getTotalPaddingBottom() {
        return getExtendedPaddingBottom() + getBottomVerticalOffset(true);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        if (left != getPaddingLeft() ||
                right != getPaddingRight() ||
                top != getPaddingTop() ||
                bottom != getPaddingBottom()) {
            nullLayouts();
        }

        // the super call will requestLayout()
        super.setPadding(left, top, right, bottom);
        invalidate();
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        if (start != getPaddingStart() ||
                end != getPaddingEnd() ||
                top != getPaddingTop() ||
                bottom != getPaddingBottom()) {
            nullLayouts();
        }

        // the super call will requestLayout()
        super.setPaddingRelative(start, top, end, bottom);
        invalidate();
    }

    /**
     * Gets the autolink mask of the text.  See {@link
     * Linkify#ALL Linkify.ALL} and peers for
     * possible values.
     *
     * @attr ref android.R.styleable#TextView_autoLink
     */
    public final int getAutoLinkMask() {
        return mAutoLinkMask;
    }

    /**
     * Sets the autolink mask of the text.  See {@link
     * Linkify#ALL Linkify.ALL} and peers for
     * possible values.
     *
     * @attr ref android.R.styleable#TextView_autoLink
     */
    public final void setAutoLinkMask(int mask) {
        mAutoLinkMask = mask;
    }

    /**
     * Sets the text color, size, style, hint color, and highlight color
     * from the specified TextAppearance resource.
     */
    public void setTextAppearance(Context context, int resid) {
    }

    /**
     * Get the default {@link Locale} of the text in this TextView.
     *
     * @return the default {@link Locale} of the text in this TextView.
     */
    public Locale getTextLocale() {
        return mTextPaint.getTextLocale();
    }

    /**
     * Set the default {@link Locale} of the text in this TextView to the given value. This value
     * is used to choose appropriate typefaces for ambiguous characters. Typically used for CJK
     * locales to disambiguate Hanzi/Kanji/Hanja characters.
     *
     * @param locale the {@link Locale} for drawing text, must not be null.
     * @see Paint#setTextLocale
     */
    public void setTextLocale(Locale locale) {
        mTextPaint.setTextLocale(locale);
    }

    /**
     * @return the size (in pixels) of the default text size in this TextView.
     */
    public float getTextSize() {
        return mTextPaint.getTextSize();
    }

    /**
     * Set the default text size to the given value, interpreted as "scaled
     * pixel" units.  This size is adjusted based on the current density and
     * user font size preference.
     *
     * @param size The scaled pixel size.
     * @attr ref android.R.styleable#TextView_textSize
     */
    public void setTextSize(float size) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    }

    /**
     * @return the size (in scaled pixels) of thee default text size in this TextView.
     * @hide
     */
    public float getScaledTextSize() {
        return mTextPaint.getTextSize() / mTextPaint.density;
    }

    public int getTypefaceStyle() {
        return mTextPaint.getTypeface().getStyle();
    }

    /**
     * Set the default text size to a given unit and value.  See {@link
     * TypedValue} for the possible dimension units.
     *
     * @param unit The desired dimension unit.
     * @param size The desired size in the given units.
     * @attr ref android.R.styleable#TextView_textSize
     */
    public void setTextSize(int unit, float size) {
        Context c = getContext();
        Resources r;

        if (c == null)
            r = Resources.getSystem();
        else
            r = c.getResources();

        setRawTextSize(TypedValue.applyDimension(
                unit, size, r.getDisplayMetrics()));
    }

    private void setRawTextSize(float size) {
        if (size != mTextPaint.getTextSize()) {
            mTextPaint.setTextSize(size);
            onTextSizeChanged();
            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * @return the extent by which text is currently being stretched
     * horizontally.  This will usually be 1.
     */
    public float getTextScaleX() {
        return mTextPaint.getTextScaleX();
    }

    /**
     * Sets the extent by which text should be stretched horizontally.
     *
     * @attr ref android.R.styleable#TextView_textScaleX
     */
    public void setTextScaleX(float size) {
        if (size != mTextPaint.getTextScaleX()) {
            mUserSetTextScaleX = true;
            mTextPaint.setTextScaleX(size);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * @return the current typeface and style in which the text is being
     * displayed.
     * @attr ref android.R.styleable#TextView_fontFamily
     * @attr ref android.R.styleable#TextView_typeface
     * @attr ref android.R.styleable#TextView_textStyle
     * @see #setTypeface(Typeface)
     */
    public Typeface getTypeface() {
        return mTextPaint.getTypeface();
    }

    /**
     * Sets the typeface and style in which the text should be displayed.
     * Note that not all Typeface families actually have bold and italic
     * variants, so you may need to use
     * {@link #setTypeface(Typeface, int)} to get the appearance
     * that you actually want.
     *
     * @attr ref android.R.styleable#TextView_fontFamily
     * @attr ref android.R.styleable#TextView_typeface
     * @attr ref android.R.styleable#TextView_textStyle
     * @see #getTypeface()
     */
    public void setTypeface(Typeface tf) {
        if (mTextPaint.getTypeface() != tf) {
            mTextPaint.setTypeface(tf);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets the text color for all the states (normal, selected,
     * focused) to be this color.
     *
     * @attr ref android.R.styleable#TextView_textColor
     * @see #setTextColor(ColorStateList)
     * @see #getTextColors()
     */
    public void setTextColor(int color) {
        mTextColor = ColorStateList.valueOf(color);
        updateTextColors();
    }

    /**
     * Sets the text color.
     *
     * @attr ref android.R.styleable#TextView_textColor
     * @see #setTextColor(int)
     * @see #getTextColors()
     * @see #setHintTextColor(ColorStateList)
     * @see #setLinkTextColor(ColorStateList)
     */
    public void setTextColor(ColorStateList colors) {
        if (colors == null) {
            throw new NullPointerException();
        }

        mTextColor = colors;
        updateTextColors();
    }

    /**
     * Gets the text colors for the different states (normal, selected, focused) of the TextView.
     *
     * @attr ref android.R.styleable#TextView_textColor
     * @see #setTextColor(ColorStateList)
     * @see #setTextColor(int)
     */
    public final ColorStateList getTextColors() {
        return mTextColor;
    }

    /**
     * <p>Return the current color selected for normal text.</p>
     *
     * @return Returns the current text color.
     */
    public final int getCurrentTextColor() {
        return mCurTextColor;
    }

    /**
     * @return the color used to display the selection highlight
     * @attr ref android.R.styleable#TextView_textColorHighlight
     * @see #setHighlightColor(int)
     */
    public int getHighlightColor() {
        return mHighlightColor;
    }

    /**
     * Sets the color used to display the selection highlight.
     *
     * @attr ref android.R.styleable#TextView_textColorHighlight
     */
    public void setHighlightColor(int color) {
        if (mHighlightColor != color) {
            mHighlightColor = color;
            invalidate();
        }
    }

    /**
     * Returns whether the soft input method will be made visible when this
     * TextView gets focused. The default is true.
     */
    public final boolean getShowSoftInputOnFocus() {
        // When there is no Editor, return default true value
        return mEditor == null || mEditor.mShowSoftInputOnFocus;
    }

    /**
     * Sets whether the soft input method will be made visible when this
     * TextView gets focused. The default is true.
     */
    public final void setShowSoftInputOnFocus(boolean show) {
        createEditorIfNeeded();
        mEditor.mShowSoftInputOnFocus = show;
    }

    /**
     * Gives the text a shadow of the specified blur radius and color, the specified
     * distance from its drawn position.
     * <p>
     * The text shadow produced does not interact with the properties on view
     * that are responsible for real time shadows,
     * {@link View#getElevation() elevation} and
     * {@link View#getTranslationZ() translationZ}.
     *
     * @attr ref android.R.styleable#TextView_shadowColor
     * @attr ref android.R.styleable#TextView_shadowDx
     * @attr ref android.R.styleable#TextView_shadowDy
     * @attr ref android.R.styleable#TextView_shadowRadius
     * @see Paint#setShadowLayer(float, float, float, int)
     */
    public void setShadowLayer(float radius, float dx, float dy, int color) {
        mTextPaint.setShadowLayer(radius, dx, dy, color);

        mShadowRadius = radius;
        mShadowDx = dx;
        mShadowDy = dy;
        mShadowColor = color;

        // Will change text clip region
        if (mEditor != null) mEditor.invalidateTextDisplayList();
        invalidate();
    }

    /**
     * Gets the radius of the shadow layer.
     *
     * @return the radius of the shadow layer. If 0, the shadow layer is not visible
     * @attr ref android.R.styleable#TextView_shadowRadius
     * @see #setShadowLayer(float, float, float, int)
     */
    public float getShadowRadius() {
        return mShadowRadius;
    }

    /**
     * @return the horizontal offset of the shadow layer
     * @attr ref android.R.styleable#TextView_shadowDx
     * @see #setShadowLayer(float, float, float, int)
     */
    public float getShadowDx() {
        return mShadowDx;
    }

    /**
     * @return the vertical offset of the shadow layer
     * @attr ref android.R.styleable#TextView_shadowDy
     * @see #setShadowLayer(float, float, float, int)
     */
    public float getShadowDy() {
        return mShadowDy;
    }

    /**
     * @return the color of the shadow layer
     * @attr ref android.R.styleable#TextView_shadowColor
     * @see #setShadowLayer(float, float, float, int)
     */
    public int getShadowColor() {
        return mShadowColor;
    }

    /**
     * @return the base paint used for the text.  Please use this only to
     * consult the Paint's properties and not to change them.
     */
    public TextPaint getPaint() {
        return mTextPaint;
    }

    /**
     * Returns whether the movement method will automatically be set to
     * {@link android.text.method.LinkMovementMethod} if {@link #setAutoLinkMask} has been
     * set to nonzero and links are detected in {@link #setText}.
     * The default is true.
     *
     * @attr ref android.R.styleable#TextView_linksClickable
     */
    public final boolean getLinksClickable() {
        return mLinksClickable;
    }

    /**
     * Sets whether the movement method will automatically be set to
     * {@link android.text.method.LinkMovementMethod} if {@link #setAutoLinkMask} has been
     * set to nonzero and links are detected in {@link #setText}.
     * The default is true.
     *
     * @attr ref android.R.styleable#TextView_linksClickable
     */
    public final void setLinksClickable(boolean whether) {
        mLinksClickable = whether;
    }

    /**
     * Returns the list of URLSpans attached to the text
     * (by {@link Linkify} or otherwise) if any.  You can call
     * {@link URLSpan#getURL} on them to find where they link to
     * or use {@link Spanned#getSpanStart} and {@link Spanned#getSpanEnd}
     * to find the region of the text they are attached to.
     */
    public URLSpan[] getUrls() {
        if (mText instanceof Spanned) {
            return ((Spanned) mText).getSpans(0, mText.length(), URLSpan.class);
        } else {
            return new URLSpan[0];
        }
    }

    /**
     * Sets the color of the hint text for all the states (disabled, focussed, selected...) of this
     * TextView.
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     * @see #setHintTextColor(ColorStateList)
     * @see #getHintTextColors()
     * @see #setTextColor(int)
     */
    public final void setHintTextColor(int color) {
        mHintTextColor = ColorStateList.valueOf(color);
        updateTextColors();
    }

    /**
     * Sets the color of the hint text.
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     * @see #getHintTextColors()
     * @see #setHintTextColor(int)
     * @see #setTextColor(ColorStateList)
     * @see #setLinkTextColor(ColorStateList)
     */
    public final void setHintTextColor(ColorStateList colors) {
        mHintTextColor = colors;
        updateTextColors();
    }

    /**
     * @return the color of the hint text, for the different states of this TextView.
     * @attr ref android.R.styleable#TextView_textColorHint
     * @see #setHintTextColor(ColorStateList)
     * @see #setHintTextColor(int)
     * @see #setTextColor(ColorStateList)
     * @see #setLinkTextColor(ColorStateList)
     */
    public final ColorStateList getHintTextColors() {
        return mHintTextColor;
    }

    /**
     * <p>Return the current color selected to paint the hint text.</p>
     *
     * @return Returns the current hint text color.
     */
    public final int getCurrentHintTextColor() {
        return mHintTextColor != null ? mCurHintTextColor : mCurTextColor;
    }

    /**
     * Sets the color of links in the text.
     *
     * @attr ref android.R.styleable#TextView_textColorLink
     * @see #setLinkTextColor(ColorStateList)
     * @see #getLinkTextColors()
     */
    public final void setLinkTextColor(int color) {
        mLinkTextColor = ColorStateList.valueOf(color);
        updateTextColors();
    }

    /**
     * Sets the color of links in the text.
     *
     * @attr ref android.R.styleable#TextView_textColorLink
     * @see #setLinkTextColor(int)
     * @see #getLinkTextColors()
     * @see #setTextColor(ColorStateList)
     * @see #setHintTextColor(ColorStateList)
     */
    public final void setLinkTextColor(ColorStateList colors) {
        mLinkTextColor = colors;
        updateTextColors();
    }

    /**
     * @return the list of colors used to paint the links in the text, for the different states of
     * this TextView
     * @attr ref android.R.styleable#TextView_textColorLink
     * @see #setLinkTextColor(ColorStateList)
     * @see #setLinkTextColor(int)
     */
    public final ColorStateList getLinkTextColors() {
        return mLinkTextColor;
    }

    /**
     * Returns the horizontal and vertical alignment of this TextView.
     *
     * @attr ref android.R.styleable#TextView_gravity
     * @see Gravity
     */
    public int getGravity() {
        return mGravity;
    }

    /**
     * Sets the horizontal alignment of the text and the
     * vertical gravity that will be used when there is extra space
     * in the TextView beyond what is required for the text itself.
     *
     * @attr ref android.R.styleable#TextView_gravity
     * @see Gravity
     */
    public void setGravity(int gravity) {
        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
            gravity |= Gravity.START;
        }
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
            gravity |= Gravity.TOP;
        }

        boolean newLayout = false;

        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) !=
                (mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)) {
            newLayout = true;
        }

        if (gravity != mGravity) {
            invalidate();
        }

        mGravity = gravity;

        if (mLayout != null && newLayout) {
            // XXX this is heavy-handed because no actual content changes.
            int want = mLayout.getWidth();
            int hintWant = mHintLayout == null ? 0 : mHintLayout.getWidth();

            makeNewLayout(want, hintWant, UNKNOWN_BORING, UNKNOWN_BORING,
                    getRight() - getLeft() - getCompoundPaddingLeft() -
                            getCompoundPaddingRight(), true);
        }
    }

    /**
     * @return the flags on the Paint being used to display the text.
     * @see Paint#getFlags
     */
    public int getPaintFlags() {
        return mTextPaint.getFlags();
    }

    /**
     * Sets flags on the Paint being used to display the text and
     * reflows the text if they are different from the old flags.
     *
     * @see Paint#setFlags
     */
    public void setPaintFlags(int flags) {
        if (mTextPaint.getFlags() != flags) {
            mTextPaint.setFlags(flags);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Returns whether the text is allowed to be wider than the View is.
     * If false, the text will be wrapped to the width of the View.
     *
     * @attr ref android.R.styleable#TextView_scrollHorizontally
     * @hide
     */
    public boolean getHorizontallyScrolling() {
        return mHorizontallyScrolling;
    }

    /**
     * Sets whether the text should be allowed to be wider than the
     * View is.  If false, it will be wrapped to the width of the View.
     *
     * @attr ref android.R.styleable#TextView_scrollHorizontally
     */
    public void setHorizontallyScrolling(boolean whether) {
        if (mHorizontallyScrolling != whether) {
            mHorizontallyScrolling = whether;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * @return the minimum number of lines displayed in this TextView, or -1 if the minimum
     * height was set in pixels instead using {@link #setMinHeight(int) or #setHeight(int)}.
     * @attr ref android.R.styleable#TextView_minLines
     * @see #setMinLines(int)
     */
    public int getMinLines() {
        return mMinMode == LINES ? mMinimum : -1;
    }

    /**
     * Makes the TextView at least this many lines tall.
     * <p>
     * Setting this value overrides any other (minimum) height setting. A single line TextView will
     * set this value to 1.
     *
     * @attr ref android.R.styleable#TextView_minLines
     * @see #getMinLines()
     */
    public void setMinLines(int minlines) {
        mMinimum = minlines;
        mMinMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * @return the minimum height of this TextView expressed in pixels, or -1 if the minimum
     * height was set in number of lines instead using {@link #setMinLines(int) or #setLines(int)}.
     * @attr ref android.R.styleable#TextView_minHeight
     * @see #setMinHeight(int)
     */
    public int getMinHeight() {
        return mMinMode == PIXELS ? mMinimum : -1;
    }

    /**
     * Makes the TextView at least this many pixels tall.
     * <p>
     * Setting this value overrides any other (minimum) number of lines setting.
     *
     * @attr ref android.R.styleable#TextView_minHeight
     */
    public void setMinHeight(int minHeight) {
        mMinimum = minHeight;
        mMinMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the maximum number of lines displayed in this TextView, or -1 if the maximum
     * height was set in pixels instead using {@link #setMaxHeight(int) or #setHeight(int)}.
     * @attr ref android.R.styleable#TextView_maxLines
     * @see #setMaxLines(int)
     */
    public int getMaxLines() {
        return mMaxMode == LINES ? mMaximum : -1;
    }

    /**
     * Makes the TextView at most this many lines tall.
     * <p>
     * Setting this value overrides any other (maximum) height setting.
     *
     * @attr ref android.R.styleable#TextView_maxLines
     */
    public void setMaxLines(int maxlines) {
        mMaximum = maxlines;
        mMaxMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * @return the maximum height of this TextView expressed in pixels, or -1 if the maximum
     * height was set in number of lines instead using {@link #setMaxLines(int) or #setLines(int)}.
     * @attr ref android.R.styleable#TextView_maxHeight
     * @see #setMaxHeight(int)
     */
    public int getMaxHeight() {
        return mMaxMode == PIXELS ? mMaximum : -1;
    }

    /**
     * Makes the TextView at most this many pixels tall.  This option is mutually exclusive with the
     * {@link #setMaxLines(int)} method.
     * <p>
     * Setting this value overrides any other (maximum) number of lines setting.
     *
     * @attr ref android.R.styleable#TextView_maxHeight
     */
    public void setMaxHeight(int maxHeight) {
        mMaximum = maxHeight;
        mMaxMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Makes the TextView exactly this many lines tall.
     * <p>
     * Note that setting this value overrides any other (minimum / maximum) number of lines or
     * height setting. A single line TextView will set this value to 1.
     *
     * @attr ref android.R.styleable#TextView_lines
     */
    public void setLines(int lines) {
        mMaximum = mMinimum = lines;
        mMaxMode = mMinMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * Makes the TextView exactly this many pixels tall.
     * You could do the same thing by specifying this number in the
     * LayoutParams.
     * <p>
     * Note that setting this value overrides any other (minimum / maximum) number of lines or
     * height setting.
     *
     * @attr ref android.R.styleable#TextView_height
     */
    public void setHeight(int pixels) {
        mMaximum = mMinimum = pixels;
        mMaxMode = mMinMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the minimum width of the TextView, expressed in ems or -1 if the minimum width
     * was set in pixels instead (using {@link #setMinWidth(int)} or {@link #setWidth(int)}).
     * @attr ref android.R.styleable#TextView_minEms
     * @see #setMinEms(int)
     * @see #setEms(int)
     */
    public int getMinEms() {
        return mMinWidthMode == EMS ? mMinWidth : -1;
    }

//    @Override
//    public void drawableHotspotChanged(float x, float y) {
//        super.drawableHotspotChanged(x, y);

//        final Drawables dr = mDrawables;
//        if (dr != null) {
//            if (dr.mDrawableTop != null) {
//                dr.mDrawableTop.setHotspot(x, y);
//            }
//            if (dr.mDrawableBottom != null) {
//                dr.mDrawableBottom.setHotspot(x, y);
//            }
//            if (dr.mDrawableLeft != null) {
//                dr.mDrawableLeft.setHotspot(x, y);
//            }
//            if (dr.mDrawableRight != null) {
//                dr.mDrawableRight.setHotspot(x, y);
//            }
//            if (dr.mDrawableStart != null) {
//                dr.mDrawableStart.setHotspot(x, y);
//            }
//            if (dr.mDrawableEnd != null) {
//                dr.mDrawableEnd.setHotspot(x, y);
//            }
//        }
//    }

    /**
     * Makes the TextView at least this many ems wide
     *
     * @attr ref android.R.styleable#TextView_minEms
     */
    public void setMinEms(int minems) {
        mMinWidth = minems;
        mMinWidthMode = EMS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the minimum width of the TextView, in pixels or -1 if the minimum width
     * was set in ems instead (using {@link #setMinEms(int)} or {@link #setEms(int)}).
     * @attr ref android.R.styleable#TextView_minWidth
     * @see #setMinWidth(int)
     * @see #setWidth(int)
     */
    public int getMinWidth() {
        return mMinWidthMode == PIXELS ? mMinWidth : -1;
    }

    /**
     * Makes the TextView at least this many pixels wide
     *
     * @attr ref android.R.styleable#TextView_minWidth
     */
    public void setMinWidth(int minpixels) {
        mMinWidth = minpixels;
        mMinWidthMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the maximum width of the TextView, expressed in ems or -1 if the maximum width
     * was set in pixels instead (using {@link #setMaxWidth(int)} or {@link #setWidth(int)}).
     * @attr ref android.R.styleable#TextView_maxEms
     * @see #setMaxEms(int)
     * @see #setEms(int)
     */
    public int getMaxEms() {
        return mMaxWidthMode == EMS ? mMaxWidth : -1;
    }

    /**
     * Makes the TextView at most this many ems wide
     *
     * @attr ref android.R.styleable#TextView_maxEms
     */
    public void setMaxEms(int maxems) {
        mMaxWidth = maxems;
        mMaxWidthMode = EMS;

        requestLayout();
        invalidate();
    }

    ///////////////////////////////////////////////////////////////////////////

//    /**
//     * Sets the Factory used to create new Editables.
//     */
//    public final void setEditableFactory(Editable.Factory factory) {
//        mEditableFactory = factory;
//        setText(mText);
//    }

    /**
     * @return the maximum width of the TextView, in pixels or -1 if the maximum width
     * was set in ems instead (using {@link #setMaxEms(int)} or {@link #setEms(int)}).
     * @attr ref android.R.styleable#TextView_maxWidth
     * @see #setMaxWidth(int)
     * @see #setWidth(int)
     */
    public int getMaxWidth() {
        return mMaxWidthMode == PIXELS ? mMaxWidth : -1;
    }

    /**
     * Makes the TextView at most this many pixels wide
     *
     * @attr ref android.R.styleable#TextView_maxWidth
     */
    public void setMaxWidth(int maxpixels) {
        mMaxWidth = maxpixels;
        mMaxWidthMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Makes the TextView exactly this many ems wide
     *
     * @attr ref android.R.styleable#TextView_ems
     * @see #setMaxEms(int)
     * @see #setMinEms(int)
     * @see #getMinEms()
     * @see #getMaxEms()
     */
    public void setEms(int ems) {
        mMaxWidth = mMinWidth = ems;
        mMaxWidthMode = mMinWidthMode = EMS;

        requestLayout();
        invalidate();
    }

    /**
     * Makes the TextView exactly this many pixels wide.
     * You could do the same thing by specifying this number in the
     * LayoutParams.
     *
     * @attr ref android.R.styleable#TextView_width
     * @see #setMaxWidth(int)
     * @see #setMinWidth(int)
     * @see #getMinWidth()
     * @see #getMaxWidth()
     */
    public void setWidth(int pixels) {
        mMaxWidth = mMinWidth = pixels;
        mMaxWidthMode = mMinWidthMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets line spacing for this TextView.  Each line will have its height
     * multiplied by <code>mult</code> and have <code>add</code> added to it.
     *
     * @attr ref android.R.styleable#TextView_lineSpacingExtra
     * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
     */
    public void setLineSpacing(float add, float mult) {
        if (mSpacingAdd != add || mSpacingMult != mult) {
            mSpacingAdd = add;
            mSpacingMult = mult;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Gets the line spacing multiplier
     *
     * @return the value by which each line's height is multiplied to get its actual height.
     * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacingExtra()
     */
    public float getLineSpacingMultiplier() {
        return mSpacingMult;
    }

    /**
     * Gets the line spacing extra space
     *
     * @return the extra space that is added to the height of each lines of this TextView.
     * @attr ref android.R.styleable#TextView_lineSpacingExtra
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacingMultiplier()
     */
    public float getLineSpacingExtra() {
        return mSpacingAdd;
    }

    /**
     * Convenience method: Append the specified text to the TextView's
     * display buffer, upgrading it to BufferType.EDITABLE if it was
     * not already editable.
     */
    public final void append(CharSequence text) {
        append(text, 0, text.length());
    }

    /**
     * Convenience method: Append the specified text slice to the TextView's
     * display buffer, upgrading it to BufferType.EDITABLE if it was
     * not already editable.
     */
    public void append(CharSequence text, int start, int end) {
        if (!(mText instanceof Editable)) {
            setText(mText, BufferType.EDITABLE);
        }

        ((Editable) mText).append(text, start, end);
    }

    private void updateTextColors() {
        boolean inval = false;
        int color = mTextColor.getColorForState(getDrawableState(), 0);
        if (color != mCurTextColor) {
            mCurTextColor = color;
            inval = true;
        }
        if (mLinkTextColor != null) {
            color = mLinkTextColor.getColorForState(getDrawableState(), 0);
            if (color != mTextPaint.linkColor) {
                mTextPaint.linkColor = color;
                inval = true;
            }
        }
        if (mHintTextColor != null) {
            color = mHintTextColor.getColorForState(getDrawableState(), 0);
            if (color != mCurHintTextColor && mText.length() == 0) {
                mCurHintTextColor = color;
                inval = true;
            }
        }
        if (inval) {
            // Text needs to be redrawn with the new color
            if (mEditor != null) mEditor.invalidateTextDisplayList();
            invalidate();
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mTextColor != null && mTextColor.isStateful()
                || (mHintTextColor != null && mHintTextColor.isStateful())
                || (mLinkTextColor != null && mLinkTextColor.isStateful())) {
            updateTextColors();
        }

//        final Drawables dr = mDrawables;
//        if (dr != null) {
//            int[] state = getDrawableState();
//            if (dr.mDrawableTop != null && dr.mDrawableTop.isStateful()) {
//                dr.mDrawableTop.setState(state);
//            }
//            if (dr.mDrawableBottom != null && dr.mDrawableBottom.isStateful()) {
//                dr.mDrawableBottom.setState(state);
//            }
//            if (dr.mDrawableLeft != null && dr.mDrawableLeft.isStateful()) {
//                dr.mDrawableLeft.setState(state);
//            }
//            if (dr.mDrawableRight != null && dr.mDrawableRight.isStateful()) {
//                dr.mDrawableRight.setState(state);
//            }
//            if (dr.mDrawableStart != null && dr.mDrawableStart.isStateful()) {
//                dr.mDrawableStart.setState(state);
//            }
//            if (dr.mDrawableEnd != null && dr.mDrawableEnd.isStateful()) {
//                dr.mDrawableEnd.setState(state);
//            }
//        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        // Save state if we are forced to
        boolean save = mFreezesText;
        int start = 0;
        int end = 0;

        if (mText != null) {
            start = getSelectionStart();
            end = getSelectionEnd();
            if (start >= 0 || end >= 0) {
                // Or save state if there is a selection
                save = true;
            }
        }

        if (save) {
            SavedState ss = new SavedState(superState);
            // XXX Should also save the current scroll position!
            ss.selStart = start;
            ss.selEnd = end;

//            if (mText instanceof Spanned) {
//                Spannable sp = new SpannableStringBuilder(mText);
//
//                if (mEditor != null) {
//                    removeMisspelledSpans(sp);
//                    sp.removeSpan(mEditor.mSuggestionRangeSpan);
//                }
//
//                ss.text = sp;
//            } else {
//                ss.text = mText.toString();
//            }
            if (mText instanceof SpannableStringBuilder) {
                SpannableStringBuilder ssb = (SpannableStringBuilder) mText;
                ss.text = ssb.toCharArray();
                ss.textLength = ssb.length();
            }

            if (isFocused() && start >= 0 && end >= 0) {
                ss.frozenWithFocus = true;
            }

            ss.error = getError();

            return ss;
        }

        return superState;
    }

    void removeMisspelledSpans(Spannable spannable) {
        SuggestionSpan[] suggestionSpans = spannable.getSpans(0, spannable.length(),
                SuggestionSpan.class);
        for (int i = 0; i < suggestionSpans.length; i++) {
            int flags = suggestionSpans[i].getFlags();
            if ((flags & SuggestionSpan.FLAG_EASY_CORRECT) != 0
                    && (flags & SuggestionSpan.FLAG_MISSPELLED) != 0) {
                spannable.removeSpan(suggestionSpans[i]);
            }
        }
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        if (ss.getSuperState() != null) //旋转屏幕时，不需要设置这里
            super.onRestoreInstanceState(ss.getSuperState());

        // XXX restore buffer type too, as well as lots of other stuff
        if (ss.text != null) {
            //setText(ss.text);
            setText(new SpannableStringBuilder(ss.text, 0, ss.textLength));
        }

        if (ss.selStart >= 0 && ss.selEnd >= 0) {
            if (mText instanceof Spannable) {
                int len = mText.length();

                if (ss.selStart > len || ss.selEnd > len) {
                    String restored = "";

                    if (ss.text != null) {
                        restored = "(restored) ";
                    }

                    Log.e(LOG_TAG, "Saved cursor position " + ss.selStart +
                            "/" + ss.selEnd + " out of range for " + restored +
                            "text " + mText);
                } else {
                    Selection.setSelection((Spannable) mText, ss.selStart, ss.selEnd);

                    if (ss.frozenWithFocus) {
                        createEditorIfNeeded();
                        mEditor.mFrozenWithFocus = true;
                    }
                }
            }
        }

        if (ss.error != null) {
            final CharSequence error = ss.error;
            // Display the error later, after the first layout pass
            post(new Runnable() {
                public void run() {
                    setError(error);
                }
            });
        }
    }

    /**
     * Return whether this text view is including its entire text contents
     * in frozen icicles.
     *
     * @return Returns true if text is included, false if it isn't.
     * @see #setFreezesText
     */
    public boolean getFreezesText() {
        return mFreezesText;
    }

    /**
     * Control whether this text view saves its entire text contents when
     * freezing to an icicle, in addition to dynamic state such as cursor
     * position.  By default this is false, not saving the text.  Set to true
     * if the text in the text view is not being saved somewhere else in
     * persistent storage (such as in a content provider) so that if the
     * view is later thawed the user will not lose their data.
     *
     * @param freezesText Controls whether a frozen icicle should include the
     *                    entire text data: true to include it, false to not.
     * @attr ref android.R.styleable#TextView_freezesText
     */
    public void setFreezesText(boolean freezesText) {
        mFreezesText = freezesText;
    }

    /**
     * Sets the Factory used to create new Spannables.
     */
    public final void setSpannableFactory(Spannable.Factory factory) {
        mSpannableFactory = factory;
        setText(mText);
    }

    /**
     * Like {@link #setText(CharSequence)},
     * except that the cursor position (if any) is retained in the new text.
     *
     * @param text The new text to place in the text view.
     * @see #setText(CharSequence)
     */
    public final void setTextKeepState(CharSequence text) {
        setTextKeepState(text, mBufferType);
    }

    /**
     * Sets the text that this TextView is to display (see
     * {@link #setText(CharSequence)}) and also sets whether it is stored
     * in a styleable/spannable buffer and whether it is editable.
     *
     * @attr ref android.R.styleable#TextView_text
     * @attr ref android.R.styleable#TextView_bufferType
     */
    public void setText(CharSequence text, BufferType type) {
        setText(text, type, true, 0);

//        if (mCharWrapper != null) {
//            mCharWrapper.mChars = null;
//        }
    }

    private void setText(CharSequence text, BufferType type,
                         boolean notifyBefore, int oldlen) {
        if (text == null) {
            text = "";
        }

        // If suggestions are not enabled, remove the suggestion spans from the text
        if (!isSuggestionsEnabled()) {
            text = removeSuggestionSpans(text);
        }

        if (!mUserSetTextScaleX) mTextPaint.setTextScaleX(1.0f);


        int n = mFilters.length;
        for (int i = 0; i < n; i++) {
            CharSequence out = mFilters[i].filter(text, 0, text.length(), EMPTY_SPANNED, 0, 0);
            if (out != null) {
                text = out;
            }
        }

        if (notifyBefore) {
            if (mText != null) {
                oldlen = mText.length();
                sendBeforeTextChanged(mText, 0, oldlen, text.length());
            } else {
                sendBeforeTextChanged("", 0, 0, text.length());
            }
        }

        boolean needEditableForNotification = false;

        if (mListeners != null && mListeners.size() != 0) {
            needEditableForNotification = true;
        }

        if (type == BufferType.EDITABLE || getKeyListener() != null ||
                needEditableForNotification) {
            createEditorIfNeeded();
//            Editable t = mEditableFactory.newEditable(text); //jec-
            Editable t = new SpannableStringBuilder(text);
            text = t;
            setFilters(t, mFilters);
            InputMethodManager imm = InputMethodManagerCompat.peekInstance();
            if (imm != null) imm.restartInput(this);
        } else if (type == BufferType.SPANNABLE || mMovement != null) {
            text = mSpannableFactory.newSpannable(text);
//        } else if (!(text instanceof CharWrapper)) { //jec-
        } else { //jec+
            text = TextUtils.stringOrSpannedString(text);
        }

        if (mAutoLinkMask != 0) {
            Spannable s2;

            if (type == BufferType.EDITABLE || text instanceof Spannable) {
                s2 = (Spannable) text;
            } else {
                s2 = mSpannableFactory.newSpannable(text);
            }

            if (Linkify.addLinks(s2, mAutoLinkMask)) {
                text = s2;
                type = (type == BufferType.EDITABLE) ? BufferType.EDITABLE : BufferType.SPANNABLE;

                /*
                 * We must go ahead and set the text before changing the
                 * movement method, because setMovementMethod() may call
                 * setText() again to try to upgrade the buffer type.
                 */
                mText = text;

                // Do not change the movement method for text that support text selection as it
                // would prevent an arbitrary cursor displacement.
                if (mLinksClickable && !textCanBeSelected()) {
                    setMovementMethod(LinkMovementMethod.getInstance());
                }
            }
        }

        mBufferType = type;
        mText = text;

        if (mTransformation == null) {
            mTransformed = text;
        } else {
            mTransformed = mTransformation.getTransformation(text, this);
        }

        final int textLength = text.length();

        if (text instanceof Spannable && !mAllowTransformationLengthChange) {
            Spannable sp = (Spannable) text;

            // Remove any ChangeWatchers that might have come from other TextViews.
            final ChangeWatcher[] watchers = sp.getSpans(0, sp.length(), ChangeWatcher.class);
            final int count = watchers.length;
            for (int i = 0; i < count; i++) {
                sp.removeSpan(watchers[i]);
            }

            if (mChangeWatcher == null) mChangeWatcher = new ChangeWatcher(this);

            sp.setSpan(mChangeWatcher, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE |
                    (CHANGE_WATCHER_PRIORITY << Spanned.SPAN_PRIORITY_SHIFT));

            if (mEditor != null) mEditor.addSpanWatchers(sp);

            if (mTransformation != null) {
                sp.setSpan(mTransformation, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }

            if (mMovement != null) {
                mMovement.initialize(this, (Spannable) text);

                /*
                 * Initializing the movement method will have set the
                 * selection, so reset mSelectionMoved to keep that from
                 * interfering with the normal on-focus selection-setting.
                 */
                if (mEditor != null) mEditor.mSelectionMoved = false;
            }
        }

        if (mLayout != null) {
            checkForRelayout();
        }

        sendOnTextChanged(text, 0, oldlen, textLength);
        onTextChanged(text, 0, oldlen, textLength);

//        notifyViewAccessibilityStateChangedIfNeeded(AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT);

        if (needEditableForNotification) {
            sendAfterTextChanged((Editable) text);
        }

        // SelectionModifierCursorController depends on textCanBeSelected, which depends on text
        if (mEditor != null) mEditor.prepareCursorControllers();

        if (pref != null && mText instanceof SpannableStringBuilder) {
            ((SpannableStringBuilder) mText).setAutoIndent(pref.isAutoIndent());
        }
    }

    /**
     * Like {@link #setText(CharSequence, BaseEditorView.BufferType)},
     * except that the cursor position (if any) is retained in the new text.
     *
     * @see #setText(CharSequence, BaseEditorView.BufferType)
     */
    public final void setTextKeepState(CharSequence text, BufferType type) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        int len = text.length();

        setText(text, type);

        if (start >= 0 || end >= 0) {
            if (mText instanceof Spannable) {
                Selection.setSelection((Spannable) mText,
                        Math.max(0, Math.min(start, len)),
                        Math.max(0, Math.min(end, len)));
            }
        }
    }

    public final void setText(int resid, BufferType type) {
        setText(getContext().getResources().getText(resid), type);
    }

    /**
     * Sets the text to be displayed when the text of the TextView is empty.
     * Null means to use the normal empty text. The hint does not currently
     * participate in determining the size of the view.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    public final void setHint(CharSequence hint) {
        mHint = TextUtils.stringOrSpannedString(hint);

        if (mLayout != null) {
            checkForRelayout();
        }

        if (mText.length() == 0) {
            invalidate();
        }

        // Invalidate display list if hint is currently used
        if (mEditor != null && mText.length() == 0 && mHint != null) {
            mEditor.invalidateTextDisplayList();
        }
    }

    /**
     * Returns the hint that is displayed when the text of the TextView
     * is empty.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    public CharSequence getHint() {
        return mHint;
    }

    /**
     * Sets the text to be displayed when the text of the TextView is empty,
     * from a resource.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    public final void setHint(int resid) {
        setHint(getContext().getResources().getText(resid));
    }

    boolean isSingleLine() {
        return mSingleLine;
    }

    /**
     * If true, sets the properties of this field (number of lines, horizontally scrolling,
     * transformation method) to be for a single-line input; if false, restores these to the default
     * conditions.
     * <p>
     * Note that the default conditions are not necessarily those that were in effect prior this
     * method, and you may want to reset these properties to your custom values.
     *
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public void setSingleLine(boolean singleLine) {
        // Could be used, but may break backward compatibility.
        // if (mSingleLine == singleLine) return;
        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, true, true);
    }

    /**
     * Removes the suggestion spans.
     */
    CharSequence removeSuggestionSpans(CharSequence text) {
        if (text instanceof Spanned) {
            Spannable spannable;
            if (text instanceof Spannable) {
                spannable = (Spannable) text;
            } else {
                spannable = new SpannableString(text);
                text = spannable;
            }

            SuggestionSpan[] spans = spannable.getSpans(0, text.length(), SuggestionSpan.class);
            for (int i = 0; i < spans.length; i++) {
                spannable.removeSpan(spans[i]);
            }
        }
        return text;
    }

    /**
     * It would be better to rely on the input type for everything. A password inputType should have
     * a password transformation. We should hence use isPasswordInputType instead of this method.
     * <p>
     * We should:
     * - Call setInputType in setKeyListener instead of changing the input type directly (which
     * would install the correct transformation).
     * - Refuse the installation of a non-password transformation in setTransformation if the input
     * type is password.
     * <p>
     * However, this is like this for legacy reasons and we cannot break existing apps. This method
     * is useful since it matches what the user can see (obfuscated text or not).
     *
     * @return true if the current transformation method is of the password type.
     */
    private boolean hasPasswordTransformationMethod() {
        return mTransformation instanceof PasswordTransformationMethod;
    }

    /**
     * Directly change the content type integer of the text view, without
     * modifying any other state.
     *
     * @attr ref android.R.styleable#TextView_inputType
     * @see #setInputType(int)
     * @see InputType
     */
    public void setRawInputType(int type) {
        if (type == InputType.TYPE_NULL && mEditor == null) return; //TYPE_NULL is the default value
        createEditorIfNeeded();
        mEditor.mInputType = type;
    }

    private void setInputType(int type, boolean direct) {
        final int cls = type & EditorInfo.TYPE_MASK_CLASS;
        KeyListener input;
        if (cls == EditorInfo.TYPE_CLASS_TEXT) {
            boolean autotext = (type & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0;
            TextKeyListener.Capitalize cap;
            if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
                cap = TextKeyListener.Capitalize.CHARACTERS;
            } else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
                cap = TextKeyListener.Capitalize.WORDS;
            } else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                cap = TextKeyListener.Capitalize.SENTENCES;
            } else {
                cap = TextKeyListener.Capitalize.NONE;
            }
            input = TextKeyListener.getInstance(autotext, cap);
        } else if (cls == EditorInfo.TYPE_CLASS_NUMBER) {
            input = DigitsKeyListener.getInstance(
                    (type & EditorInfo.TYPE_NUMBER_FLAG_SIGNED) != 0,
                    (type & EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0);
        } else if (cls == EditorInfo.TYPE_CLASS_DATETIME) {
            switch (type & EditorInfo.TYPE_MASK_VARIATION) {
                case EditorInfo.TYPE_DATETIME_VARIATION_DATE:
                    input = DateKeyListener.getInstance();
                    break;
                case EditorInfo.TYPE_DATETIME_VARIATION_TIME:
                    input = TimeKeyListener.getInstance();
                    break;
                default:
                    input = DateTimeKeyListener.getInstance();
                    break;
            }
        } else if (cls == EditorInfo.TYPE_CLASS_PHONE) {
            input = DialerKeyListener.getInstance();
        } else {
            input = TextKeyListener.getInstance();
        }
        setRawInputType(type);
        if (direct) {
            createEditorIfNeeded();
            mEditor.mKeyListener = input;
        } else {
            setKeyListenerOnly(input);
        }
    }

    /**
     * Get the type of the editable content.
     *
     * @see #setInputType(int)
     * @see InputType
     */
    public int getInputType() {
        return mEditor == null ? EditorInfo.TYPE_NULL : mEditor.mInputType;
    }

    /**
     * Set the type of the content with a constant as defined for {@link EditorInfo#inputType}. This
     * will take care of changing the key listener, by calling {@link #setKeyListener(KeyListener)},
     * to match the given content type.  If the given content type is {@link EditorInfo#TYPE_NULL}
     * then a soft keyboard will not be displayed for this text view.
     * <p>
     * Note that the maximum number of displayed lines (see {@link #setMaxLines(int)}) will be
     * modified if you change the {@link EditorInfo#TYPE_TEXT_FLAG_MULTI_LINE} flag of the input
     * type.
     *
     * @attr ref android.R.styleable#TextView_inputType
     * @see #getInputType()
     * @see #setRawInputType(int)
     * @see InputType
     */
    public void setInputType(int type) {
        final boolean wasPassword = isPasswordInputType(getInputType());
        final boolean wasVisiblePassword = isVisiblePasswordInputType(getInputType());
        setInputType(type, false);
        final boolean isPassword = isPasswordInputType(type);
        final boolean isVisiblePassword = isVisiblePasswordInputType(type);
        boolean forceUpdate = false;
        if (isPassword) {
            setTransformationMethod(PasswordTransformationMethod.getInstance());
            setTypefaceFromAttrs(null /* fontFamily */, MONOSPACE, 0);
        } else if (isVisiblePassword) {
            if (mTransformation == PasswordTransformationMethod.getInstance()) {
                forceUpdate = true;
            }
            setTypefaceFromAttrs(null /* fontFamily */, MONOSPACE, 0);
        } else if (wasPassword || wasVisiblePassword) {
            // not in password mode, clean up typeface and transformation
            setTypefaceFromAttrs(null /* fontFamily */, -1, -1);
            if (mTransformation == PasswordTransformationMethod.getInstance()) {
                forceUpdate = true;
            }
        }

        boolean singleLine = !isMultilineInputType(type);

        // We need to update the single line mode if it has changed or we
        // were previously in password mode.
        if (mSingleLine != singleLine || forceUpdate) {
            // Change single line mode, but only change the transformation if
            // we are not in password mode.
            applySingleLine(singleLine, !isPassword, true);
        }

        if (!isSuggestionsEnabled()) {
            mText = removeSuggestionSpans(mText);
        }

        InputMethodManager imm = InputMethodManagerCompat.peekInstance();
        if (imm != null) imm.restartInput(this);
    }

    /**
     * Get the type of the IME editor.
     *
     * @see #setImeOptions(int)
     * @see EditorInfo
     */
    public int getImeOptions() {
        return mEditor != null && mEditor.mInputContentType != null
                ? mEditor.mInputContentType.imeOptions : EditorInfo.IME_NULL;
    }

    /**
     * Change the editor type integer associated with the text view, which
     * will be reported to an IME with {@link EditorInfo#imeOptions} when it
     * has focus.
     *
     * @attr ref android.R.styleable#TextView_imeOptions
     * @see #getImeOptions
     * @see EditorInfo
     */
    public void setImeOptions(int imeOptions) {
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.imeOptions = imeOptions;
    }

    /**
     * Change the custom IME action associated with the text view, which
     * will be reported to an IME with {@link EditorInfo#actionLabel}
     * and {@link EditorInfo#actionId} when it has focus.
     *
     * @attr ref android.R.styleable#TextView_imeActionLabel
     * @attr ref android.R.styleable#TextView_imeActionId
     * @see #getImeActionLabel
     * @see #getImeActionId
     * @see EditorInfo
     */
    public void setImeActionLabel(CharSequence label, int actionId) {
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.imeActionLabel = label;
        mEditor.mInputContentType.imeActionId = actionId;
    }

//    @Override
//    protected boolean setFrame(int l, int t, int r, int b) {
//        boolean result = super.setFrame(l, t, r, b);
//
//        if (mEditor != null) mEditor.setFrame();
//
//        restartMarqueeIfNeeded();
//
//        return result;
//    }

    /**
     * Get the IME action label previous set with {@link #setImeActionLabel}.
     *
     * @see #setImeActionLabel
     * @see EditorInfo
     */
    public CharSequence getImeActionLabel() {
        return mEditor != null && mEditor.mInputContentType != null
                ? mEditor.mInputContentType.imeActionLabel : null;
    }

    /**
     * Get the IME action ID previous set with {@link #setImeActionLabel}.
     *
     * @see #setImeActionLabel
     * @see EditorInfo
     */
    public int getImeActionId() {
        return mEditor != null && mEditor.mInputContentType != null
                ? mEditor.mInputContentType.imeActionId : 0;
    }

    /**
     * Set a special listener to be called when an action is performed
     * on the text view.  This will be called when the enter key is pressed,
     * or when an action supplied to the IME is selected by the user.  Setting
     * this means that the normal hard key event will not insert a newline
     * into the text view, even if it is multi-line; holding down the ALT
     * modifier will, however, allow the user to insert a newline character.
     */
    public void setOnEditorActionListener(OnEditorActionListener l) {
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.onEditorActionListener = l;
    }

    /**
     * Called when an attached input method calls
     * {@link InputConnection#performEditorAction(int)
     * InputConnection.performEditorAction()}
     * for this text view.  The default implementation will call your action
     * listener supplied to {@link #setOnEditorActionListener}, or perform
     * a standard operation for {@link EditorInfo#IME_ACTION_NEXT
     * EditorInfo.IME_ACTION_NEXT}, {@link EditorInfo#IME_ACTION_PREVIOUS
     * EditorInfo.IME_ACTION_PREVIOUS}, or {@link EditorInfo#IME_ACTION_DONE
     * EditorInfo.IME_ACTION_DONE}.
     * <p>
     * <p>For backwards compatibility, if no IME options have been set and the
     * text view would not normally advance focus on enter, then
     * the NEXT and DONE actions received here will be turned into an enter
     * key down/up pair to go through the normal key handling.
     *
     * @param actionCode The code of the action being performed.
     * @see #setOnEditorActionListener
     */
    public void onEditorAction(int actionCode) {
        final Editor.InputContentType ict = mEditor == null ? null : mEditor.mInputContentType;
        if (ict != null) {
            if (ict.onEditorActionListener != null) {
                if (ict.onEditorActionListener.onEditorAction(this,
                        actionCode, null)) {
                    return;
                }
            }

        }
    }

    /////////////////////////////////////////////////////////////////////////

    /**
     * Get the private type of the content.
     *
     * @see #setPrivateImeOptions(String)
     * @see EditorInfo#privateImeOptions
     */
    public String getPrivateImeOptions() {
        return mEditor != null && mEditor.mInputContentType != null
                ? mEditor.mInputContentType.privateImeOptions : null;
    }

    /**
     * Set the private content type of the text, which is the
     * {@link EditorInfo#privateImeOptions EditorInfo.privateImeOptions}
     * field that will be filled in when creating an input connection.
     *
     * @attr ref android.R.styleable#TextView_privateImeOptions
     * @see #getPrivateImeOptions()
     * @see EditorInfo#privateImeOptions
     */
    public void setPrivateImeOptions(String type) {
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.privateImeOptions = type;
    }

    /**
     * Set the extra input data of the text, which is the
     * {@link EditorInfo#extras TextBoxAttribute.extras}
     * Bundle that will be filled in when creating an input connection.  The
     * given integer is the resource ID of an XML resource holding an
     * {link android.R.styleable#InputExtras &lt;input-extras&gt;} XML tree.
     *
     * @attr ref android.R.styleable#TextView_editorExtras
     * @see #getInputExtras(boolean)
     * @see EditorInfo#extras
     */
    public void setInputExtras(int xmlResId) throws XmlPullParserException, IOException {
        createEditorIfNeeded();
        XmlResourceParser parser = getResources().getXml(xmlResId);
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.extras = new Bundle();
        getResources().parseBundleExtras(parser, mEditor.mInputContentType.extras);
    }

    /**
     * Retrieve the input extras currently associated with the text view, which
     * can be viewed as well as modified.
     *
     * @param create If true, the extras will be created if they don't already
     *               exist.  Otherwise, null will be returned if none have been created.
     * @attr ref android.R.styleable#TextView_editorExtras
     * @see #setInputExtras(int)
     * @see EditorInfo#extras
     */
    public Bundle getInputExtras(boolean create) {
        if (mEditor == null && !create) return null;
        createEditorIfNeeded();
        if (mEditor.mInputContentType == null) {
            if (!create) return null;
            mEditor.createInputContentTypeIfNeeded();
        }
        if (mEditor.mInputContentType.extras == null) {
            if (!create) return null;
            mEditor.mInputContentType.extras = new Bundle();
        }
        return mEditor.mInputContentType.extras;
    }

    /**
     * Returns the error message that was set to be displayed with
     * {@link #setError}, or <code>null</code> if no error was set
     * or if it the error was cleared by the widget after user input.
     */
    public CharSequence getError() {
        return mEditor == null ? null : mEditor.mError;
    }

    /**
     * Sets the right-hand compound drawable of the TextView to the "error"
     * icon and sets an error message that will be displayed in a popup when
     * the TextView has focus.  The icon and error message will be reset to
     * null when any key events cause changes to the TextView's text.  If the
     * <code>error</code> is <code>null</code>, the error message and icon
     * will be cleared.
     */
    public void setError(CharSequence error) {
//        if (error == null) {
//            setError(null, null);
//        } else {
//            Drawable dr = getContext().getDrawable(
//                    android.R.drawable.indicator_input_error);
//
//            dr.setBounds(0, 0, dr.getIntrinsicWidth(), dr.getIntrinsicHeight());
//            setError(error, dr);
//        }
    }

    /**
     * Sets the right-hand compound drawable of the TextView to the specified
     * icon and sets an error message that will be displayed in a popup when
     * the TextView has focus.  The icon and error message will be reset to
     * null when any key events cause changes to the TextView's text.  The
     * drawable must already have had {@link Drawable#setBounds} set on it.
     * If the <code>error</code> is <code>null</code>, the error message will
     * be cleared (and you should provide a <code>null</code> icon as well).
     */
    public void setError(CharSequence error, Drawable icon) {
        createEditorIfNeeded();
//        mEditor.setError(error, icon);
//        notifyViewAccessibilityStateChangedIfNeeded(
//                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
    }

    private void restartMarqueeIfNeeded() {
//        if (mRestartMarquee && mEllipsize == TruncateAt.MARQUEE) {
//            mRestartMarquee = false;
//            startMarquee();
//        }
    }

    /**
     * Sets the list of input filters on the specified Editable,
     * and includes mInput in the list if it is an InputFilter.
     */
    private void setFilters(Editable e, InputFilter[] filters) {
        if (mEditor != null) {
            final boolean undoFilter = mEditor.mUndoInputFilter != null;
            final boolean keyFilter = mEditor.mKeyListener instanceof InputFilter;
            int num = 0;
            if (undoFilter) num++;
            if (keyFilter) num++;
            if (num > 0) {
                InputFilter[] nf = new InputFilter[filters.length + num];

                System.arraycopy(filters, 0, nf, 0, filters.length);
                num = 0;
                if (undoFilter) {
                    nf[filters.length] = mEditor.mUndoInputFilter;
                    num++;
                }
                if (keyFilter) {
                    nf[filters.length + num] = (InputFilter) mEditor.mKeyListener;
                }

                e.setFilters(nf);
                return;
            }
        }
        e.setFilters(filters);
    }

    /**
     * Returns the current list of input filters.
     *
     * @attr ref android.R.styleable#TextView_maxLength
     */
    public InputFilter[] getFilters() {
        return mFilters;
    }

    /**
     * Sets the list of input filters that will be used if the buffer is
     * Editable. Has no effect otherwise.
     *
     * @attr ref android.R.styleable#TextView_maxLength
     */
    public void setFilters(InputFilter[] filters) {
        if (filters == null) {
            throw new IllegalArgumentException();
        }

        mFilters = filters;

        if (mText instanceof Editable) {
            setFilters((Editable) mText, filters);
        }
    }

    private int getBoxHeight(Layout l) {
//        Insets opticalInsets = isLayoutModeOptical(mParent) ? getOpticalInsets() : Insets.NONE;
        int padding = (l == mHintLayout) ?
                getCompoundPaddingTop() + getCompoundPaddingBottom() :
                getExtendedPaddingTop() + getExtendedPaddingBottom();
//        return getMeasuredHeight() - padding + opticalInsets.top + opticalInsets.bottom;
        return getMeasuredHeight() - padding;
    }

    int getVerticalOffset(boolean forceNormal) {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) {
            l = mHintLayout;
        }

        if (gravity != Gravity.TOP) {
            int boxht = getBoxHeight(l);
            int textht = l.getHeight();

            if (textht < boxht) {
                if (gravity == Gravity.BOTTOM)
                    voffset = boxht - textht;
                else // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
            }
        }
        return voffset;
    }

    private int getBottomVerticalOffset(boolean forceNormal) {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) {
            l = mHintLayout;
        }

        if (gravity != Gravity.BOTTOM) {
            int boxht = getBoxHeight(l);
            int textht = l.getHeight();

            if (textht < boxht) {
                if (gravity == Gravity.TOP)
                    voffset = boxht - textht;
                else // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
            }
        }
        return voffset;
    }

    /* 显示光标 */
    void invalidateCursorPath() {
        if (mHighlightPathBogus) {
            invalidateCursor();
        } else {
            final int horizontalPadding = getCompoundPaddingLeft();
            final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

            if (mEditor.mCursorCount == 0) {
                synchronized (TEMP_RECTF) {
                    /*
                     * The reason for this concern about the thickness of the
                     * cursor and doing the floor/ceil on the coordinates is that
                     * some EditTexts (notably textfields in the Browser) have
                     * anti-aliased text where not all the characters are
                     * necessarily at integer-multiple locations.  This should
                     * make sure the entire cursor gets invalidated instead of
                     * sometimes missing half a pixel.
                     */
                    float thick = (float) Math.ceil(mTextPaint.getStrokeWidth());
                    if (thick < 1.0f) {
                        thick = 1.0f;
                    }

                    thick /= 2.0f;

                    // mHighlightPath is guaranteed to be non null at that point.
                    mHighlightPath.computeBounds(TEMP_RECTF, false);

                    invalidate((int) Math.floor(horizontalPadding + TEMP_RECTF.left - thick),
                            (int) Math.floor(verticalPadding + TEMP_RECTF.top - thick),
                            (int) Math.ceil(horizontalPadding + TEMP_RECTF.right + thick),
                            (int) Math.ceil(verticalPadding + TEMP_RECTF.bottom + thick));
                }
            } else {
                for (int i = 0; i < mEditor.mCursorCount; i++) {
                    Rect bounds = mEditor.mCursorDrawable[i].getBounds();
                    invalidate(bounds.left + horizontalPadding, bounds.top + verticalPadding,
                            bounds.right + horizontalPadding, bounds.bottom + verticalPadding);
                }
            }
        }
    }

    void invalidateCursor() {
        int where = getSelectionEnd();

        invalidateCursor(where, where, where);
    }

    private void invalidateCursor(int a, int b, int c) {
        if (a >= 0 || b >= 0 || c >= 0) {
            int start = Math.min(Math.min(a, b), c);
            int end = Math.max(Math.max(a, b), c);
            invalidateRegion(start, end, true /* Also invalidates blinking cursor */);
        }
    }

    /**
     * Invalidates the region of text enclosed between the start and end text offsets.
     */
    void invalidateRegion(int start, int end, boolean invalidateCursor) {
        if (mLayout == null) {
            invalidate();
        } else {
            int lineStart = mLayout.getLineForOffset(start);
            int top = mLayout.getLineTop(lineStart);

            // This is ridiculous, but the descent from the line above
            // can hang down into the line we really want to redraw,
            // so we have to invalidate part of the line above to make
            // sure everything that needs to be redrawn really is.
            // (But not the whole line above, because that would cause
            // the same problem with the descenders on the line above it!)
            if (lineStart > 0) {
                top -= mLayout.getLineDescent(lineStart - 1);
            }

            int lineEnd;

            if (start == end)
                lineEnd = lineStart;
            else
                lineEnd = mLayout.getLineForOffset(end);

            int bottom = mLayout.getLineBottom(lineEnd);

            // mEditor can be null in case selection is set programmatically.
            if (invalidateCursor && mEditor != null) {
                for (int i = 0; i < mEditor.mCursorCount; i++) {
                    Rect bounds = mEditor.mCursorDrawable[i].getBounds();
                    top = Math.min(top, bounds.top);
                    bottom = Math.max(bottom, bounds.bottom);
                }
            }

            final int compoundPaddingLeft = getCompoundPaddingLeft();
            final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

            int left, right;
            if (lineStart == lineEnd && !invalidateCursor) {
                left = (int) mLayout.getPrimaryHorizontal(start);
                right = (int) (mLayout.getPrimaryHorizontal(end) + 1.0);
                left += compoundPaddingLeft;
                right += compoundPaddingLeft;
            } else {
                // Rectangle bounding box when the region spans several lines
                left = compoundPaddingLeft;
                right = getWidth() - getCompoundPaddingRight();
            }

            invalidate(getScrollX() + left, verticalPadding + top,
                    getScrollX() + right, verticalPadding + bottom);
        }
    }

    private void registerForPreDraw() {
        if (!mPreDrawRegistered) {
            getViewTreeObserver().addOnPreDrawListener(this);
            mPreDrawRegistered = true;
        }
    }

    private void unregisterForPreDraw() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        mPreDrawRegistered = false;
        mPreDrawListenerDetached = false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean onPreDraw() {
        if (mLayout == null) {
            assumeLayout();
        }

        if (mMovement != null) {
            /* This code also provides auto-scrolling when a cursor is moved using a
             * CursorController (insertion point or selection limits).
             * For selection, ensure start or end is visible depending on controller's state.
             */
            int curs = getSelectionEnd();
            // Do not create the controller if it is not already created.
            if (mEditor != null && mEditor.mSelectionModifierCursorController != null &&
                    mEditor.mSelectionModifierCursorController.isSelectionStartDragged()) {
                curs = getSelectionStart();
            }

            /*
             * TODO: This should really only keep the end in view if
             * it already was before the text changed.  I'm not sure
             * of a good way to tell from here if it was.
             */
            if (curs < 0 && (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                curs = mText.length();
            }

            if (curs >= 0 && mLastCursorOffset != curs) {
                mLastCursorOffset = curs;
                bringPointIntoView(curs);
            }
        } else {
            bringTextIntoView();
        }

        // This has to be checked here since:
        // - onFocusChanged cannot start it when focus is given to a view with selected text (after
        //   a screen rotation) since layout is not yet initialized at that point.
        if (mEditor != null && mEditor.mCreatedWithASelection) {
            mEditor.startSelectionActionMode();
            mEditor.mCreatedWithASelection = false;
        }

        // Phone specific code (there is no ExtractEditText on tablets).
        // ExtractEditText does not call onFocus when it is displayed, and mHasSelectionOnFocus can
        // not be set. Do the test here instead.
//        if (this instanceof ExtractEditText && hasSelection() && mEditor != null) {
//            mEditor.startSelectionActionMode();
//        }

        unregisterForPreDraw();

        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mTemporaryDetach = false;

        if (mEditor != null) mEditor.onAttachedToWindow();

        if (mPreDrawListenerDetached) {
            getViewTreeObserver().addOnPreDrawListener(this);
            mPreDrawListenerDetached = false;
        }
    }

    /**
     * @hide
     */
    @Override
//    protected void onDetachedFromWindowInternal() {
    protected void onDetachedFromWindow() {
        if (mPreDrawRegistered) {
            getViewTreeObserver().removeOnPreDrawListener(this);
            mPreDrawListenerDetached = true;
        }

//        resetResolvedDrawables();

        if (mEditor != null) mEditor.onDetachedFromWindow();

//        super.onDetachedFromWindowInternal();
        super.onDetachedFromWindow();
    }

    @Override
    public void onScreenStateChanged(int screenState) {
        super.onScreenStateChanged(screenState);
        if (mEditor != null) mEditor.onScreenStateChanged(screenState);
    }

    @Override
    protected boolean isPaddingOffsetRequired() {
//        return mShadowRadius != 0 || mDrawables != null;
        return false;
    }

    @Override
    protected int getLeftPaddingOffset() {
        return getCompoundPaddingLeft() - getPaddingLeft() +
                (int) Math.min(0, mShadowDx - mShadowRadius);
    }

    @Override
    protected int getTopPaddingOffset() {
        return (int) Math.min(0, mShadowDy - mShadowRadius);
    }

    @Override
    protected int getBottomPaddingOffset() {
        return (int) Math.max(0, mShadowDy + mShadowRadius);
    }

    @Override
    protected int getRightPaddingOffset() {
        return -(getCompoundPaddingRight() - getPaddingRight()) +
                (int) Math.max(0, mShadowDx + mShadowRadius);
    }

    @Override
    public boolean hasOverlappingRendering() {
        // horizontal fading edge causes SaveLayerAlpha, which doesn't support alpha modulation
        return ((getBackground() != null && getBackground().getCurrent() != null)
                || mText instanceof Spannable || hasSelection()
                || isHorizontalFadingEdgeEnabled());
    }

    /**
     * Returns the state of the {@code textIsSelectable} flag (See
     * {@link #setTextIsSelectable setTextIsSelectable()}). Although you have to set this flag
     * to allow users to select and copy text in a non-editable TextView, the content of an
     * {@link android.widget.EditText} can always be selected, independently of the value of this flag.
     * <p>
     *
     * @return True if the text displayed in this TextView can be selected by the user.
     * @attr ref android.R.styleable#TextView_textIsSelectable
     */
    public boolean isTextSelectable() {
        return mEditor == null ? false : mEditor.mTextIsSelectable;
    }

    /**
     * Sets whether the content of this view is selectable by the user. The default is
     * {@code false}, meaning that the content is not selectable.
     * <p>
     * When you use a TextView to display a useful piece of information to the user (such as a
     * contact's address), make it selectable, so that the user can select and copy its
     * content. You can also use set the XML attribute
     * {link android.R.styleable#TextView_textIsSelectable} to "true".
     * <p>
     * When you call this method to set the value of {@code textIsSelectable}, it sets
     * the flags {@code focusable}, {@code focusableInTouchMode}, {@code clickable},
     * and {@code longClickable} to the same value. These flags correspond to the attributes
     * {link android.R.styleable#View_focusable android:focusable},
     * {link android.R.styleable#View_focusableInTouchMode android:focusableInTouchMode},
     * {link android.R.styleable#View_clickable android:clickable}, and
     * {link android.R.styleable#View_longClickable android:longClickable}. To restore any of these
     * flags to a state you had set previously, call one or more of the following methods:
     * {@link #setFocusable(boolean) setFocusable()},
     * {@link #setFocusableInTouchMode(boolean) setFocusableInTouchMode()},
     * {@link #setClickable(boolean) setClickable()} or
     * {@link #setLongClickable(boolean) setLongClickable()}.
     *
     * @param selectable Whether the content of this TextView should be selectable.
     */
    public void setTextIsSelectable(boolean selectable) {
        if (!selectable && mEditor == null) return; // false is default value with no edit data

        createEditorIfNeeded();
        if (mEditor.mTextIsSelectable == selectable) return;

        mEditor.mTextIsSelectable = selectable;
        setFocusableInTouchMode(selectable);
        setFocusable(selectable);
        setClickable(selectable);
        setLongClickable(selectable);

        // mInputType should already be EditorInfo.TYPE_NULL and mInput should be null

        setMovementMethod(selectable ? ArrowKeyMovementMethod.getInstance() : null);
        setText(mText, selectable ? BufferType.SPANNABLE : BufferType.NORMAL);

        // Called by setText above, but safer in case of future code changes
        mEditor.prepareCursorControllers();
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState;

        if (mSingleLine) {
            drawableState = super.onCreateDrawableState(extraSpace);
        } else {
            drawableState = super.onCreateDrawableState(extraSpace + 1);
//            mergeDrawableStates(drawableState, MULTILINE_STATE_SET);
        }

        return drawableState;
    }

    private Path getUpdatedHighlightPath() {
        Path highlight = null;
        Paint highlightPaint = mHighlightPaint;

        final int selStart = getSelectionStart();
        final int selEnd = getSelectionEnd();
        if (mMovement != null && (isFocused() || isPressed()) && selStart >= 0) {
            if (selStart == selEnd) {
                if (mEditor != null && mEditor.isCursorVisible() &&
                        (SystemClock.uptimeMillis() - mEditor.mShowCursor) %
                                (2 * Editor.BLINK) < Editor.BLINK) {
                    if (mHighlightPathBogus) {
                        if (mHighlightPath == null) mHighlightPath = new Path();
                        mHighlightPath.reset();
                        mLayout.getCursorPath(selStart, mHighlightPath, mText);
                        mEditor.updateCursorsPositions();
                        mHighlightPathBogus = false;
                    }

                    // XXX should pass to skin instead of drawing directly
                    highlightPaint.setColor(mCurTextColor);
//                    highlightPaint.setStyle(Paint.Style.STROKE); //jec-
                    highlightPaint.setStyle(Paint.Style.FILL); //实心
                    highlight = mHighlightPath;
                }
            } else {
                if (mHighlightPathBogus) {
                    if (mHighlightPath == null) mHighlightPath = new Path();
                    mHighlightPath.reset();
                    mLayout.getSelectionPath(selStart, selEnd, mHighlightPath);
                    mHighlightPathBogus = false;
                }

                // XXX should pass to skin instead of drawing directly
                highlightPaint.setColor(mHighlightColor);
                highlightPaint.setStyle(Paint.Style.FILL);

                highlight = mHighlightPath;
            }
        }
        return highlight;
    }

    /**
     * @hide
     */
    public int getHorizontalOffsetForDrawables() {
        return 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        restartMarqueeIfNeeded();

        // Draw the background for this view
        super.onDraw(canvas);

        final int compoundPaddingLeft = getCompoundPaddingLeft();
        final int compoundPaddingTop = getCompoundPaddingTop();
        final int compoundPaddingRight = getCompoundPaddingRight();
        final int compoundPaddingBottom = getCompoundPaddingBottom();
        final int scrollX = getScrollX(); // getScrollX();
        final int scrollY = getScrollY();
        final int right = getRight();
        final int left = getLeft();
        final int bottom = getBottom();
        final int top = getTop();

        int color = mCurTextColor;

        if (mLayout == null) {
            assumeLayout();
        }

        Layout layout = mLayout;

        if (mHint != null && mText.length() == 0) {
            if (mHintTextColor != null) {
                color = mCurHintTextColor;
            }

            layout = mHintLayout;
        }

        mTextPaint.setColor(color);
        mTextPaint.drawableState = getDrawableState();

        canvas.save();
        /*  Would be faster if we didn't have to do this. Can we chop the
            (displayable) text so that we don't need to do this ever?
        */

        int extendedPaddingTop = getExtendedPaddingTop();
        int extendedPaddingBottom = getExtendedPaddingBottom();

        final int vspace = getBottom() - getTop() - compoundPaddingBottom - compoundPaddingTop;
        final int maxScrollY = mLayout.getHeight() - vspace;

        float clipLeft = compoundPaddingLeft + scrollX;
        float clipTop = (scrollY == 0) ? 0 : extendedPaddingTop + scrollY;
        float clipRight = right - left - compoundPaddingRight + scrollX;
        float clipBottom = bottom - top + scrollY -
                ((scrollY == maxScrollY) ? 0 : extendedPaddingBottom);

        if (mShadowRadius != 0) {
            clipLeft += Math.min(0, mShadowDx - mShadowRadius);
            clipRight += Math.max(0, mShadowDx + mShadowRadius);

            clipTop += Math.min(0, mShadowDy - mShadowRadius);
            clipBottom += Math.max(0, mShadowDy + mShadowRadius);
        }

        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);

        int voffsetText = 0;
        int voffsetCursor = 0;

        // translate in by our padding
        /* shortcircuit calling getVerticaOffset() */
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffsetText = getVerticalOffset(false);
            voffsetCursor = getVerticalOffset(true);
        }
        canvas.translate(compoundPaddingLeft, extendedPaddingTop + voffsetText);
        layoutContext.translateX = compoundPaddingLeft;
        layoutContext.translateY = extendedPaddingTop + voffsetText;
        layoutContext.scrollX = getScrollX();
//        final int layoutDirection = getLayoutDirection();
//        final int absoluteGravity = Gravity.getAbsoluteGravity(mGravity, layoutDirection);
//        if (mEllipsize == TruncateAt.MARQUEE &&
//                mMarqueeFadeMode != MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS) {
//            if (!mSingleLine && getLineCount() == 1 && canMarquee() &&
//                    (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) != Gravity.LEFT) {
//                final int width = getRight() - getLeft();
//                final int padding = getCompoundPaddingLeft() + getCompoundPaddingRight();
//                final float dx = mLayout.getLineRight(0) - (width - padding);
//                canvas.translate(layout.getParagraphDirection(0) * dx, 0.0f);
//            }
//
//            if (mMarquee != null && mMarquee.isRunning()) {
//                final float dx = -mMarquee.getScroll();
//                canvas.translate(layout.getParagraphDirection(0) * dx, 0.0f);
//            }
//        }

        final int cursorOffsetVertical = voffsetCursor - voffsetText;

        Path highlight = getUpdatedHighlightPath();
        if (mEditor != null) {
            mEditor.onDraw(canvas, layout, highlight, mHighlightPaint, cursorOffsetVertical);
        } else {
            layout.draw(canvas, highlight, mHighlightPaint, cursorOffsetVertical);
        }

//        if (mMarquee != null && mMarquee.shouldDrawGhost()) {
//            final float dx = mMarquee.getGhostOffset();
//            canvas.translate(layout.getParagraphDirection(0) * dx, 0.0f);
//            layout.draw(canvas, highlight, mHighlightPaint, cursorOffsetVertical);
//        }

        canvas.restore();

        drawLineNumber(canvas);
    }

    @Override
    public void getFocusedRect(Rect r) {
        if (mLayout == null) {
            super.getFocusedRect(r);
            return;
        }

        int selEnd = getSelectionEnd();
        if (selEnd < 0) {
            super.getFocusedRect(r);
            return;
        }

        int selStart = getSelectionStart();
        if (selStart < 0 || selStart >= selEnd) {
            int line = mLayout.getLineForOffset(selEnd);
            r.top = mLayout.getLineTop(line);
            r.bottom = mLayout.getLineBottom(line);
            r.left = (int) mLayout.getPrimaryHorizontal(selEnd) - 2;
            r.right = r.left + 4;
        } else {
            int lineStart = mLayout.getLineForOffset(selStart);
            int lineEnd = mLayout.getLineForOffset(selEnd);
            r.top = mLayout.getLineTop(lineStart);
            r.bottom = mLayout.getLineBottom(lineEnd);
            if (lineStart == lineEnd) {
                r.left = (int) mLayout.getPrimaryHorizontal(selStart);
                r.right = (int) mLayout.getPrimaryHorizontal(selEnd);
            } else {
                // Selection extends across multiple lines -- make the focused
                // rect cover the entire width.
                if (mHighlightPathBogus) {
                    if (mHighlightPath == null) mHighlightPath = new Path();
                    mHighlightPath.reset();
                    mLayout.getSelectionPath(selStart, selEnd, mHighlightPath);
                    mHighlightPathBogus = false;
                }
                synchronized (TEMP_RECTF) {
                    mHighlightPath.computeBounds(TEMP_RECTF, true);
                    r.left = (int) TEMP_RECTF.left - 1;
                    r.right = (int) TEMP_RECTF.right + 1;
                }
            }
        }

        // Adjust for padding and gravity.
        int paddingLeft = getCompoundPaddingLeft();
        int paddingTop = getExtendedPaddingTop();
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            paddingTop += getVerticalOffset(false);
        }
        r.offset(paddingLeft, paddingTop);
        int paddingBottom = getExtendedPaddingBottom();
        r.bottom += paddingBottom;
    }

    /**
     * Return the number of lines of text, or 0 if the internal Layout has not
     * been built.
     */
    public int getLineCount() {
        return mLayout != null ? mLayout.getLineCount() : 0;
    }

    /**
     * Return the baseline for the specified line (0...getLineCount() - 1)
     * If bounds is not null, return the top, left, right, bottom extents
     * of the specified line in it. If the internal Layout has not been built,
     * return 0 and set bounds to (0, 0, 0, 0)
     *
     * @param line   which line to examine (0..getLineCount() - 1)
     * @param bounds Optional. If not null, it returns the extent of the line
     * @return the Y-coordinate of the baseline
     */
    public int getLineBounds(int line, Rect bounds) {
        if (mLayout == null) {
            if (bounds != null) {
                bounds.set(0, 0, 0, 0);
            }
            return 0;
        } else {
            int baseline = mLayout.getLineBounds(line, bounds);

            int voffset = getExtendedPaddingTop();
            if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                voffset += getVerticalOffset(true);
            }
            if (bounds != null) {
                bounds.offset(getCompoundPaddingLeft(), voffset);
            }
            return baseline + voffset;
        }
    }

    @Override
    public int getBaseline() {
        if (mLayout == null) {
            return super.getBaseline();
        }

        int voffset = 0;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffset = getVerticalOffset(true);
        }

//        if (isLayoutModeOptical(mParent)) {
//            voffset -= getOpticalInsets().top;
//        }

        return getExtendedPaddingTop() + voffset + mLayout.getLineBaseline(0);
    }

    /**
     * @hide
     */
//    @Override
    protected int getFadeTop(boolean offsetRequired) {
        if (mLayout == null) return 0;

        int voffset = 0;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffset = getVerticalOffset(true);
        }

        if (offsetRequired) voffset += getTopPaddingOffset();

        return getExtendedPaddingTop() + voffset;
    }

    /**
     * @hide
     */
//    @Override
    protected int getFadeHeight(boolean offsetRequired) {
        return mLayout != null ? mLayout.getHeight() : 0;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            boolean isInSelectionMode = mEditor != null && mEditor.mSelectionActionMode != null;

            if (isInSelectionMode) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    KeyEvent.DispatcherState state = getKeyDispatcherState();
                    if (state != null) {
                        state.startTracking(event, this);
                    }
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    KeyEvent.DispatcherState state = getKeyDispatcherState();
                    if (state != null) {
                        state.handleUpEvent(event);
                    }
                    if (event.isTracking() && !event.isCanceled()) {
                        stopSelectionActionMode();
                        return true;
                    }
                }
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int which = doKeyDown(keyCode, event, null);
        if (which == 0) {
            return super.onKeyDown(keyCode, event);
        }

        return true;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        KeyEvent down = KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN);

        int which = doKeyDown(keyCode, down, event);
        if (which == 0) {
            // Go through default dispatching.
            return super.onKeyMultiple(keyCode, repeatCount, event);
        }
        if (which == -1) {
            // Consumed the whole thing.
            return true;
        }

        repeatCount--;

        // We are going to dispatch the remaining events to either the input
        // or movement method.  To do this, we will just send a repeated stream
        // of down and up events until we have done the complete repeatCount.
        // It would be nice if those interfaces had an onKeyMultiple() method,
        // but adding that is a more complicated change.
        KeyEvent up = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
        if (which == 1) {
            // mEditor and mEditor.mInput are not null from doKeyDown
            mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, up);
            while (--repeatCount > 0) {
                mEditor.mKeyListener.onKeyDown(this, (Editable) mText, keyCode, down);
                mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, up);
            }
            hideErrorIfUnchanged();

        } else if (which == 2) {
            // mMovement is not null from doKeyDown
            mMovement.onKeyUp(this, (Spannable) mText, keyCode, up);
            while (--repeatCount > 0) {
                mMovement.onKeyDown(this, (Spannable) mText, keyCode, down);
                mMovement.onKeyUp(this, (Spannable) mText, keyCode, up);
            }
        }

        return true;
    }

    /**
     * Returns true if pressing ENTER in this field advances focus instead
     * of inserting the character.  This is true mostly in single-line fields,
     * but also in mail addresses and subjects which will display on multiple
     * lines but where it doesn't make sense to insert newlines.
     */
    private boolean shouldAdvanceFocusOnEnter() {
        if (getKeyListener() == null) {
            return false;
        }

        if (mSingleLine) {
            return true;
        }

        if (mEditor != null &&
                (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
            int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if pressing TAB in this field advances focus instead
     * of inserting the character.  Insert tabs only in multi-line editors.
     */
    private boolean shouldAdvanceFocusOnTab() {
        if (getKeyListener() != null && !mSingleLine && mEditor != null &&
                (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
            int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
            if (variation == EditorInfo.TYPE_TEXT_FLAG_IME_MULTI_LINE
                    || variation == EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) {
                return false;
            }
        }
        return true;
    }

    private int doKeyDown(int keyCode, KeyEvent event, KeyEvent otherEvent) {
        if (!isEnabled()) {
            return 0;
        }

        // If this is the initial keydown, we don't want to prevent a movement away from this view.
        // While this shouldn't be necessary because any time we're preventing default movement we
        // should be restricting the focus to remain within this view, thus we'll also receive
        // the key up event, occasionally key up events will get dropped and we don't want to
        // prevent the user from traversing out of this on the next key down.
        if (event.getRepeatCount() == 0 && !KeyEvent.isModifierKey(keyCode)) {
            mPreventDefaultMovement = false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                if (event.hasNoModifiers()) {
                    // When mInputContentType is set, we know that we are
                    // running in a "modern" cupcake environment, so don't need
                    // to worry about the application trying to capture
                    // enter key events.
                    if (mEditor != null && mEditor.mInputContentType != null) {
                        // If there is an action listener, given them a
                        // chance to consume the event.
                        if (mEditor.mInputContentType.onEditorActionListener != null &&
                                mEditor.mInputContentType.onEditorActionListener.onEditorAction(
                                        this, EditorInfo.IME_NULL, event)) {
                            mEditor.mInputContentType.enterDown = true;
                            // We are consuming the enter key for them.
                            return -1;
                        }
                    }

                    // If our editor should move focus when enter is pressed, or
                    // this is a generated event from an IME action button, then
                    // don't let it be inserted into the text.
                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
                            || shouldAdvanceFocusOnEnter()) {
                        if (hasOnClickListeners()) {
                            return 0;
                        }
                        return -1;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.hasNoModifiers()) {
                    if (shouldAdvanceFocusOnEnter()) {
                        return 0;
                    }
                }
                break;

            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers() || event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
                    if (shouldAdvanceFocusOnTab()) {
                        return 0;
                    }
                }
                break;

            // Has to be done on key down (and not on key up) to correctly be intercepted.
            case KeyEvent.KEYCODE_BACK:
                if (mEditor != null && mEditor.mSelectionActionMode != null) {
                    stopSelectionActionMode();
                    return -1;
                }
                break;
        }

        if (mEditor != null && mEditor.mKeyListener != null) {
            boolean doDown = true;
            if (otherEvent != null) {
                try {
                    beginBatchEdit();
                    final boolean handled = mEditor.mKeyListener.onKeyOther(this, (Editable) mText,
                            otherEvent);
                    hideErrorIfUnchanged();
                    doDown = false;
                    if (handled) {
                        return -1;
                    }
                } catch (AbstractMethodError e) {
                    // onKeyOther was added after 1.0, so if it isn't
                    // implemented we need to try to dispatch as a regular down.
                } finally {
                    endBatchEdit();
                }
            }

            if (doDown) {
                beginBatchEdit();
                final boolean handled = mEditor.mKeyListener.onKeyDown(this, (Editable) mText,
                        keyCode, event);
                endBatchEdit();
                hideErrorIfUnchanged();
                if (handled) return 1;
            }
        }

        // bug 650865: sometimes we get a key event before a layout.
        // don't try to move around if we don't know the layout.

        if (mMovement != null && mLayout != null) {
            boolean doDown = true;
            if (otherEvent != null) {
                try {
                    boolean handled = mMovement.onKeyOther(this, (Spannable) mText,
                            otherEvent);
                    doDown = false;
                    if (handled) {
                        return -1;
                    }
                } catch (AbstractMethodError e) {
                    // onKeyOther was added after 1.0, so if it isn't
                    // implemented we need to try to dispatch as a regular down.
                }
            }
            if (doDown) {
                if (mMovement.onKeyDown(this, (Spannable) mText, keyCode, event)) {
                    if (event.getRepeatCount() == 0 && !KeyEvent.isModifierKey(keyCode)) {
                        mPreventDefaultMovement = true;
                    }
                    return 2;
                }
            }
        }

        return mPreventDefaultMovement && !KeyEvent.isModifierKey(keyCode) ? -1 : 0;
    }

    /**
     * Resets the mErrorWasChanged flag, so that future calls to {@link #setError(CharSequence)}
     * can be recorded.
     *
     * @hide
     */
    public void resetErrorChangedFlag() {
        /*
         * Keep track of what the error was before doing the input
         * so that if an input filter changed the error, we leave
         * that error showing.  Otherwise, we take down whatever
         * error was showing when the user types something.
         */
        if (mEditor != null) mEditor.mErrorWasChanged = false;
    }

    /**
     * @hide
     */
    public void hideErrorIfUnchanged() {
        if (mEditor != null && mEditor.mError != null && !mEditor.mErrorWasChanged) {
            setError(null, null);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!isEnabled()) {
            return super.onKeyUp(keyCode, event);
        }

        if (!KeyEvent.isModifierKey(keyCode)) {
            mPreventDefaultMovement = false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.hasNoModifiers()) {
                    /*
                     * If there is a click listener, just call through to
                     * super, which will invoke it.
                     *
                     * If there isn't a click listener, try to show the soft
                     * input method.  (It will also
                     * call performClick(), but that won't do anything in
                     * this case.)
                     */
                    if (!hasOnClickListeners()) {
                        if (mMovement != null && mText instanceof Editable
                                && mLayout != null && onCheckIsTextEditor()) {
                            InputMethodManager imm = InputMethodManagerCompat.peekInstance();
                            viewClicked(imm);
                            if (imm != null && getShowSoftInputOnFocus()) {
                                imm.showSoftInput(this, 0);
                            }
                        }
                    }
                }
                return super.onKeyUp(keyCode, event);

            case KeyEvent.KEYCODE_ENTER:
                if (event.hasNoModifiers()) {
                    if (mEditor != null && mEditor.mInputContentType != null
                            && mEditor.mInputContentType.onEditorActionListener != null
                            && mEditor.mInputContentType.enterDown) {
                        mEditor.mInputContentType.enterDown = false;
                        if (mEditor.mInputContentType.onEditorActionListener.onEditorAction(
                                this, EditorInfo.IME_NULL, event)) {
                            return true;
                        }
                    }

                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
                            || shouldAdvanceFocusOnEnter()) {
                        /*
                         * If there is a click listener, just call through to
                         * super, which will invoke it.
                         *
                         * If there isn't a click listener, try to advance focus,
                         * but still call through to super, which will reset the
                         * pressed state and longpress state.  (It will also
                         * call performClick(), but that won't do anything in
                         * this case.)
                         */
                        if (!hasOnClickListeners()) {
                            View v = focusSearch(FOCUS_DOWN);

                            if (v != null) {
                                if (!v.requestFocus(FOCUS_DOWN)) {
                                    throw new IllegalStateException(
                                            "focus search returned a view " +
                                                    "that wasn't able to take focus!");
                                }

                                /*
                                 * Return true because we handled the key; super
                                 * will return false because there was no click
                                 * listener.
                                 */
                                super.onKeyUp(keyCode, event);
                                return true;
                            } else if ((event.getFlags()
                                    & KeyEvent.FLAG_EDITOR_ACTION) != 0) {
                                // No target for next focus, but make sure the IME
                                // if this came from it.
                                InputMethodManager imm = InputMethodManagerCompat.peekInstance();
                                if (imm != null && imm.isActive(this)) {
                                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
                                }
                            }
                        }
                    }
                    return super.onKeyUp(keyCode, event);
                }
                break;
        }

        if (mEditor != null && mEditor.mKeyListener != null)
            if (mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, event))
                return true;

        if (mMovement != null && mLayout != null)
            if (mMovement.onKeyUp(this, (Spannable) mText, keyCode, event))
                return true;

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return mEditor != null && mEditor.mInputType != EditorInfo.TYPE_NULL;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (onCheckIsTextEditor() && isEnabled()) {
            mEditor.createInputMethodStateIfNeeded();
            outAttrs.inputType = getInputType();
            if (mEditor.mInputContentType != null) {
                outAttrs.imeOptions = mEditor.mInputContentType.imeOptions;
                outAttrs.privateImeOptions = mEditor.mInputContentType.privateImeOptions;
                outAttrs.actionLabel = mEditor.mInputContentType.imeActionLabel;
                outAttrs.actionId = mEditor.mInputContentType.imeActionId;
                outAttrs.extras = mEditor.mInputContentType.extras;
            } else {
                outAttrs.imeOptions = EditorInfo.IME_NULL;
            }
            if (focusSearch(FOCUS_DOWN) != null) {
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_NEXT;
            }
            if (focusSearch(FOCUS_UP) != null) {
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
            }
            if ((outAttrs.imeOptions & EditorInfo.IME_MASK_ACTION)
                    == EditorInfo.IME_ACTION_UNSPECIFIED) {
                if ((outAttrs.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0) {
                    // An action has not been set, but the enter key will move to
                    // the next focus, so set the action to that.
                    outAttrs.imeOptions |= EditorInfo.IME_ACTION_NEXT;
                } else {
                    // An action has not been set, and there is no focus to move
                    // to, so let's just supply a "done" action.
                    outAttrs.imeOptions |= EditorInfo.IME_ACTION_DONE;
                }
                if (!shouldAdvanceFocusOnEnter()) {
                    outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
                }
            }
            if (isMultilineInputType(outAttrs.inputType)) {
                // Multi-line text editors should always show an enter key.
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
            }
            outAttrs.hintText = mHint;
            if (mText instanceof Editable) {
                InputConnection ic = new EditableInputConnection(this);
                outAttrs.initialSelStart = getSelectionStart();
                outAttrs.initialSelEnd = getSelectionEnd();
                outAttrs.initialCapsMode = ic.getCursorCapsMode(getInputType());
                return ic;
            }
        }
        return null;
    }

    /**
     * If this TextView contains editable content, extract a portion of it
     * based on the information in <var>request</var> in to <var>outText</var>.
     *
     * @return Returns true if the text was successfully extracted, else false.
     * 用于输入法的复制文本操作
     */
    public boolean extractText(ExtractedTextRequest request, ExtractedText outText) {
        createEditorIfNeeded();
        return mEditor.extractText(request, outText);
    }

    /**
     * @hide
     */
    public void setExtracting(ExtractedTextRequest req) {
        if (mEditor.mInputMethodState != null) {
            mEditor.mInputMethodState.mExtractedTextRequest = req;
        }
        // This would stop a possible selection mode, but no such mode is started in case
        // extracted mode will start. Some text is selected though, and will trigger an action mode
        // in the extracted view.
        mEditor.hideControllers();
    }

    /**
     * Called by the framework in response to a text completion from
     * the current input method, provided by it calling
     * {@link InputConnection#commitCompletion
     * InputConnection.commitCompletion()}.  The default implementation does
     * nothing; text views that are supporting auto-completion should override
     * this to do their desired behavior.
     *
     * @param text The auto complete text the user has selected.
     */
    public void onCommitCompletion(CompletionInfo text) {
        // intentionally empty
    }

    /**
     * Called by the framework in response to a text auto-correction (such as fixing a typo using a
     * a dictionnary) from the current input method, provided by it calling
     * {@link InputConnection#commitCorrection} InputConnection.commitCorrection()}. The default
     * implementation flashes the background of the corrected word to provide feedback to the user.
     *
     * @param info The auto correct info about the text that was corrected.
     */
    public void onCommitCorrection(CorrectionInfo info) {
        if (mEditor != null) mEditor.onCommitCorrection(info);
    }

    public void beginBatchEdit() {
        if (mEditor != null) mEditor.beginBatchEdit();
    }

    public void endBatchEdit() {
        if (mEditor != null) mEditor.endBatchEdit();
    }

    /**
     * Called by the framework in response to a request to begin a batch
     * of edit operations through a call to link {@link #beginBatchEdit()}.
     */
    public void onBeginBatchEdit() {
        // intentionally empty
    }

    /**
     * Called by the framework in response to a request to end a batch
     * of edit operations through a call to link {@link #endBatchEdit}.
     */
    public void onEndBatchEdit() {
        // intentionally empty
    }

    /**
     * Called by the framework in response to a private command from the
     * current method, provided by it calling
     * {@link InputConnection#performPrivateCommand
     * InputConnection.performPrivateCommand()}.
     *
     * @param action The action name of the command.
     * @param data   Any additional data for the command.  This may be null.
     * @return Return true if you handled the command, else false.
     */
    public boolean onPrivateIMECommand(String action, Bundle data) {
        return false;
    }

    private void nullLayouts() {
        if (mLayout instanceof BoringLayout && mSavedLayout == null) {
            mSavedLayout = (BoringLayout) mLayout;
        }
        if (mHintLayout instanceof BoringLayout && mSavedHintLayout == null) {
            mSavedHintLayout = (BoringLayout) mHintLayout;
        }

        mSavedMarqueeModeLayout = mLayout = mHintLayout = null;

        mBoring = mHintBoring = null;

        // Since it depends on the value of mLayout
        if (mEditor != null) mEditor.prepareCursorControllers();
    }

    /**
     * Make a new Layout based on the already-measured size of the view,
     * on the assumption that it was measured correctly at some point.
     */
    private void assumeLayout() {
        int width = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();

        if (width < 1) {
            width = 0;
        }

        int physicalWidth = width;

        if (mHorizontallyScrolling) {
            width = VERY_WIDE;
        }

        makeNewLayout(width, physicalWidth, UNKNOWN_BORING, UNKNOWN_BORING,
                physicalWidth, false);
    }

    private Layout.Alignment getLayoutAlignment() {
        Layout.Alignment alignment;
        switch (getTextAlignment()) {
            case TEXT_ALIGNMENT_GRAVITY:
                switch (mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.START:
                        alignment = Layout.Alignment.ALIGN_NORMAL;
                        break;
                    case Gravity.END:
                        alignment = Layout.Alignment.ALIGN_OPPOSITE;
                        break;
                    case Gravity.LEFT:
                        alignment = Layout.Alignment.ALIGN_LEFT;
                        break;
                    case Gravity.RIGHT:
                        alignment = Layout.Alignment.ALIGN_RIGHT;
                        break;
                    case Gravity.CENTER_HORIZONTAL:
                        alignment = Layout.Alignment.ALIGN_CENTER;
                        break;
                    default:
                        alignment = Layout.Alignment.ALIGN_NORMAL;
                        break;
                }
                break;
            case TEXT_ALIGNMENT_TEXT_START:
                alignment = Layout.Alignment.ALIGN_NORMAL;
                break;
            case TEXT_ALIGNMENT_TEXT_END:
                alignment = Layout.Alignment.ALIGN_OPPOSITE;
                break;
            case TEXT_ALIGNMENT_CENTER:
                alignment = Layout.Alignment.ALIGN_CENTER;
                break;
            case TEXT_ALIGNMENT_VIEW_START:
                alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ?
                        Layout.Alignment.ALIGN_RIGHT : Layout.Alignment.ALIGN_LEFT;
                break;
            case TEXT_ALIGNMENT_VIEW_END:
                alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ?
                        Layout.Alignment.ALIGN_LEFT : Layout.Alignment.ALIGN_RIGHT;
                break;
            case TEXT_ALIGNMENT_INHERIT:
                // This should never happen as we have already resolved the text alignment
                // but better safe than sorry so we just fall through
            default:
                alignment = Layout.Alignment.ALIGN_NORMAL;
                break;
        }
        return alignment;
    }

    /**
     * The width passed in is now the desired layout width,
     * not the full view width with padding.
     * {@hide}
     */
    protected void makeNewLayout(int wantWidth, int hintWidth,
                                 BoringLayout.Metrics boring,
                                 BoringLayout.Metrics hintBoring,
                                 int ellipsisWidth, boolean bringIntoView) {
        stopMarquee();

        // Update "old" cached values
        mOldMaximum = mMaximum;
        mOldMaxMode = mMaxMode;

        mHighlightPathBogus = true;

        if (wantWidth < 0) {
            wantWidth = 0;
        }
        if (hintWidth < 0) {
            hintWidth = 0;
        }

        Layout.Alignment alignment = getLayoutAlignment();
        final boolean testDirChange = mSingleLine && mLayout != null &&
                (alignment == Layout.Alignment.ALIGN_NORMAL ||
                        alignment == Layout.Alignment.ALIGN_OPPOSITE);
        int oldDir = 0;
        if (testDirChange) oldDir = mLayout.getParagraphDirection(0);
//        boolean shouldEllipsize = mEllipsize != null && getKeyListener() == null;
//        final boolean switchEllipsize = mEllipsize == TruncateAt.MARQUEE &&
//                mMarqueeFadeMode != MARQUEE_FADE_NORMAL;
//        TruncateAt effectiveEllipsize = mEllipsize;
//        if (mEllipsize == TruncateAt.MARQUEE &&
//                mMarqueeFadeMode == MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS) {
//            effectiveEllipsize = TruncateAt.END_SMALL;
//        }

        if (mTextDir == null) {
            mTextDir = getTextDirectionHeuristic();
        }

        mLayout = makeSingleLayout(wantWidth, boring, ellipsisWidth, alignment/*, shouldEllipsize,
                effectiveEllipsize, effectiveEllipsize == mEllipsize*/);
//        if (switchEllipsize) {
//            TruncateAt oppositeEllipsize = effectiveEllipsize == TruncateAt.MARQUEE ?
//                    TruncateAt.END : TruncateAt.MARQUEE;
//            mSavedMarqueeModeLayout = makeSingleLayout(wantWidth, boring, ellipsisWidth, alignment,
//                    shouldEllipsize, oppositeEllipsize, effectiveEllipsize != mEllipsize);
//        }

//        shouldEllipsize = mEllipsize != null;
        mHintLayout = null;

        if (mHint != null) {
//            if (shouldEllipsize) hintWidth = wantWidth;

            if (hintBoring == UNKNOWN_BORING) {
                hintBoring = BoringLayout.isBoring(layoutContext, mHint, mTextPaint, mTextDir,
                        mHintBoring);
                if (hintBoring != null) {
                    mHintBoring = hintBoring;
                }
            }

            if (hintBoring != null) {
                if (hintBoring.width <= hintWidth &&
                        (/*!shouldEllipsize ||*/ hintBoring.width <= ellipsisWidth)) {
                    if (mSavedHintLayout != null) {
                        mHintLayout = mSavedHintLayout.
                                replaceOrMake(layoutContext, mHint, mTextPaint,
                                        hintWidth, alignment, mSpacingMult, mSpacingAdd,
                                        hintBoring, mIncludePad);
                    } else {
                        mHintLayout = BoringLayout.make(layoutContext, mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
                                hintBoring, mIncludePad);
                    }

                    mSavedHintLayout = (BoringLayout) mHintLayout;
//                } else if (shouldEllipsize && hintBoring.width <= hintWidth) {
//                    if (mSavedHintLayout != null) {
//                        mHintLayout = mSavedHintLayout.
//                                replaceOrMake(mHint, mTextPaint,
//                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
//                                hintBoring, mIncludePad, mEllipsize,
//                                ellipsisWidth);
//                    } else {
//                        mHintLayout = BoringLayout.make(mHint, mTextPaint,
//                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
//                                hintBoring, mIncludePad, mEllipsize,
//                                ellipsisWidth);
//                    }
//                } else if (shouldEllipsize) {
//                    mHintLayout = new StaticLayout(mHint,
//                                0, mHint.length(),
//                                mTextPaint, hintWidth, alignment, mTextDir, mSpacingMult,
//                                mSpacingAdd, mIncludePad, mEllipsize,
//                                ellipsisWidth, mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
                } else {
                    mHintLayout = new StaticLayout(layoutContext, mHint, mTextPaint,
                            hintWidth, alignment, mTextDir, mSpacingMult, mSpacingAdd,
                            mIncludePad);
                }
//            } else if (shouldEllipsize) {
//                mHintLayout = new StaticLayout(mHint,
//                            0, mHint.length(),
//                            mTextPaint, hintWidth, alignment, mTextDir, mSpacingMult,
//                            mSpacingAdd, mIncludePad, mEllipsize,
//                            ellipsisWidth, mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
            } else {
                mHintLayout = new StaticLayout(layoutContext, mHint, mTextPaint,
                        hintWidth, alignment, mTextDir, mSpacingMult, mSpacingAdd,
                        mIncludePad);
            }
        }

        if (bringIntoView || (testDirChange && oldDir != mLayout.getParagraphDirection(0))) {
            registerForPreDraw();
        }

//        if (mEllipsize == TruncateAt.MARQUEE) {
//            if (!compressText(ellipsisWidth)) {
//                final int height = mLayoutParams.height;
//                // If the size of the view does not depend on the size of the text, try to
//                // start the marquee immediately
//                if (height != LayoutParams.WRAP_CONTENT && height != LayoutParams.MATCH_PARENT) {
//                    startMarquee();
//                } else {
//                    // Defer the start of the marquee until we know our width (see setFrame())
//                    mRestartMarquee = true;
//                }
//            }
//        }

        // CursorControllers need a non-null mLayout
        if (mEditor != null) mEditor.prepareCursorControllers();
    }

    private Layout makeSingleLayout(int wantWidth, BoringLayout.Metrics boring, int ellipsisWidth,
                                    Layout.Alignment alignment/*, boolean shouldEllipsize, TruncateAt effectiveEllipsize,
            boolean useSaved*/) {
        Layout result = null;
        if (mText instanceof Spannable) {
            result = new DynamicLayout(layoutContext, mText, mTransformed, mTextPaint, wantWidth,
                    alignment, mTextDir, mSpacingMult,
                    mSpacingAdd, mIncludePad/*, getKeyListener() == null ? effectiveEllipsize : null,
                            ellipsisWidth*/);
        } else {
            if (boring == UNKNOWN_BORING) {
                boring = BoringLayout.isBoring(layoutContext, mTransformed, mTextPaint, mTextDir, mBoring);
                if (boring != null) {
                    mBoring = boring;
                }
            }

            if (boring != null) {
                if (boring.width <= wantWidth &&
                        (/*effectiveEllipsize == null || */boring.width <= ellipsisWidth)) {
                    if (/*useSaved && */mSavedLayout != null) {
                        result = mSavedLayout.replaceOrMake(layoutContext, mTransformed, mTextPaint,
                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
                                boring, mIncludePad);
                    } else {
                        result = BoringLayout.make(layoutContext, mTransformed, mTextPaint,
                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
                                boring, mIncludePad);
                    }

//                    if (useSaved) {
                    mSavedLayout = (BoringLayout) result;
//                    }
//                } else if (shouldEllipsize && boring.width <= wantWidth) {
//                    if (useSaved && mSavedLayout != null) {
//                        result = mSavedLayout.replaceOrMake(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad, effectiveEllipsize,
//                                ellipsisWidth);
//                    } else {
//                        result = BoringLayout.make(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad, effectiveEllipsize,
//                                ellipsisWidth);
//                    }
//                } else if (shouldEllipsize) {
//                    result = new StaticLayout(mTransformed,
//                            0, mTransformed.length(),
//                            mTextPaint, wantWidth, alignment, mTextDir, mSpacingMult,
//                            mSpacingAdd, mIncludePad, effectiveEllipsize,
//                            ellipsisWidth, mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
                } else {
                    result = new StaticLayout(layoutContext, mTransformed, mTextPaint,
                            wantWidth, alignment, mTextDir, mSpacingMult, mSpacingAdd,
                            mIncludePad);
                }
//            } else if (shouldEllipsize) {
//                result = new StaticLayout(mTransformed,
//                        0, mTransformed.length(),
//                        mTextPaint, wantWidth, alignment, mTextDir, mSpacingMult,
//                        mSpacingAdd, mIncludePad, effectiveEllipsize,
//                        ellipsisWidth, mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
            } else {
                result = new StaticLayout(layoutContext, mTransformed, mTextPaint,
                        wantWidth, alignment, mTextDir, mSpacingMult, mSpacingAdd,
                        mIncludePad);
            }
        }
        return result;
    }

    private boolean compressText(float width) {
        if (isHardwareAccelerated()) return false;

        // Only compress the text if it hasn't been compressed by the previous pass
        if (width > 0.0f && mLayout != null && getLineCount() == 1 && !mUserSetTextScaleX &&
                mTextPaint.getTextScaleX() == 1.0f) {
            final float textWidth = mLayout.getLineWidth(0);
            final float overflow = (textWidth + 1.0f - width) / width;
            if (overflow > 0.0f && overflow <= Marquee.MARQUEE_DELTA_MAX) {
                mTextPaint.setTextScaleX(1.0f - overflow - 0.005f);
                post(new Runnable() {
                    public void run() {
                        requestLayout();
                    }
                });
                return true;
            }
        }

        return false;
    }

    /**
     * Gets whether the TextView includes extra top and bottom padding to make
     * room for accents that go above the normal ascent and descent.
     *
     * @attr ref android.R.styleable#TextView_includeFontPadding
     * @see #setIncludeFontPadding(boolean)
     */
    public boolean getIncludeFontPadding() {
        return mIncludePad;
    }

    /**
     * Set whether the TextView includes extra top and bottom padding to make
     * room for accents that go above the normal ascent and descent.
     * The default is true.
     *
     * @attr ref android.R.styleable#TextView_includeFontPadding
     * @see #getIncludeFontPadding()
     */
    public void setIncludeFontPadding(boolean includepad) {
        if (mIncludePad != includepad) {
            mIncludePad = includepad;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        BoringLayout.Metrics boring = UNKNOWN_BORING;
        BoringLayout.Metrics hintBoring = UNKNOWN_BORING;

        if (mTextDir == null) {
            mTextDir = getTextDirectionHeuristic();
        }

        int des = -1;
        boolean fromexisting = false;

        if (widthMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            width = widthSize;
        } else {
            if (mLayout != null /*&& mEllipsize == null*/) {
                des = desired(mLayout);
            }

            if (des < 0) {
                boring = BoringLayout.isBoring(layoutContext, mTransformed, mTextPaint, mTextDir, mBoring);
                if (boring != null) {
                    mBoring = boring;
                }
            } else {
                fromexisting = true;
            }

            if (boring == null || boring == UNKNOWN_BORING) {
                if (des < 0) {
                    des = (int) Math.ceil(Layout.getDesiredWidth(layoutContext, mTransformed, mTextPaint));
                }
                width = des;
            } else {
                width = boring.width;
            }

//            final Drawables dr = mDrawables;
//            if (dr != null) {
//                width = Math.max(width, dr.mDrawableWidthTop);
//                width = Math.max(width, dr.mDrawableWidthBottom);
//            }

            if (mHint != null) {
                int hintDes = -1;
                int hintWidth;

                if (mHintLayout != null /*&& mEllipsize == null*/) {
                    hintDes = desired(mHintLayout);
                }

                if (hintDes < 0) {
                    hintBoring = BoringLayout.isBoring(layoutContext, mHint, mTextPaint, mTextDir, mHintBoring);
                    if (hintBoring != null) {
                        mHintBoring = hintBoring;
                    }
                }

                if (hintBoring == null || hintBoring == UNKNOWN_BORING) {
                    if (hintDes < 0) {
                        hintDes = (int) Math.ceil(Layout.getDesiredWidth(layoutContext, mHint, mTextPaint));
                    }
                    hintWidth = hintDes;
                } else {
                    hintWidth = hintBoring.width;
                }

                if (hintWidth > width) {
                    width = hintWidth;
                }
            }

            width += getCompoundPaddingLeft() + getCompoundPaddingRight();

            if (mMaxWidthMode == EMS) {
                width = Math.min(width, mMaxWidth * getLineHeight());
            } else {
                width = Math.min(width, mMaxWidth);
            }

            if (mMinWidthMode == EMS) {
                width = Math.max(width, mMinWidth * getLineHeight());
            } else {
                width = Math.max(width, mMinWidth);
            }

            // Check against our minimum width
            width = Math.max(width, getSuggestedMinimumWidth());

            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(widthSize, width);
            }
        }

        int want = width - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int unpaddedWidth = want;

        if (mHorizontallyScrolling) want = VERY_WIDE;

        int hintWant = want;
        int hintWidth = (mHintLayout == null) ? hintWant : mHintLayout.getWidth();

        if (mLayout == null) {
            makeNewLayout(want, hintWant, boring, hintBoring,
                    width - getCompoundPaddingLeft() - getCompoundPaddingRight(), false);
        } else {
            final boolean layoutChanged = (mLayout.getWidth() != want) ||
                    (hintWidth != hintWant) ||
                    (mLayout.getEllipsizedWidth() !=
                            width - getCompoundPaddingLeft() - getCompoundPaddingRight());

            final boolean widthChanged = (mHint == null) &&
//                    (mEllipsize == null) &&
                    (want > mLayout.getWidth()) &&
                    (mLayout instanceof BoringLayout || (fromexisting && des >= 0 && des <= want));

            final boolean maximumChanged = (mMaxMode != mOldMaxMode) || (mMaximum != mOldMaximum);

            if (layoutChanged || maximumChanged) {
                if (!maximumChanged && widthChanged) {
                    mLayout.increaseWidthTo(want);
                } else {
                    makeNewLayout(want, hintWant, boring, hintBoring,
                            width - getCompoundPaddingLeft() - getCompoundPaddingRight(), false);
                }
            } else {
                // Nothing has changed
            }
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            height = heightSize;
            mDesiredHeightAtMeasure = -1;
        } else {
            int desired = getDesiredHeight();

            height = desired;
            mDesiredHeightAtMeasure = desired;

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(desired, heightSize);
            }
        }

        int unpaddedHeight = height - getCompoundPaddingTop() - getCompoundPaddingBottom();
        if (mMaxMode == LINES && mLayout.getLineCount() > mMaximum) {
            unpaddedHeight = Math.min(unpaddedHeight, mLayout.getLineTop(mMaximum));
        }

        /*
         * We didn't let makeNewLayout() register to bring the cursor into view,
         * so do it here if there is any possibility that it is needed.
         */
        if (mMovement != null ||
                mLayout.getWidth() > unpaddedWidth ||
                mLayout.getHeight() > unpaddedHeight) {
            registerForPreDraw();
        } else {
            scrollTo(0, 0);
        }

        setMeasuredDimension(width, height);
    }

    private int getDesiredHeight() {
        return Math.max(
                getDesiredHeight(mLayout, true),
//                getDesiredHeight(mHintLayout, mEllipsize != null));
                getDesiredHeight(mHintLayout, false));
    }

    private int getDesiredHeight(Layout layout, boolean cap) {
        if (layout == null) {
            return 0;
        }

        int linecount = layout.getLineCount();
        int pad = getCompoundPaddingTop() + getCompoundPaddingBottom();
        int desired = layout.getLineTop(linecount);

//        final Drawables dr = mDrawables;
//        if (dr != null) {
//            desired = Math.max(desired, dr.mDrawableHeightLeft);
//            desired = Math.max(desired, dr.mDrawableHeightRight);
//        }

        desired += pad;

        if (mMaxMode == LINES) {
            /*
             * Don't cap the hint to a certain number of lines.
             * (Do cap it, though, if we have a maximum pixel height.)
             */
            if (cap) {
                if (linecount > mMaximum) {
                    desired = layout.getLineTop(mMaximum);

//                    if (dr != null) {
//                        desired = Math.max(desired, dr.mDrawableHeightLeft);
//                        desired = Math.max(desired, dr.mDrawableHeightRight);
//                    }

                    desired += pad;
                    linecount = mMaximum;
                }
            }
        } else {
            desired = Math.min(desired, mMaximum);
        }

        if (mMinMode == LINES) {
            if (linecount < mMinimum) {
                desired += getLineHeight() * (mMinimum - linecount);
            }
        } else {
            desired = Math.max(desired, mMinimum);
        }

        // Check against our minimum height
        desired = Math.max(desired, getSuggestedMinimumHeight());

        return desired;
    }

    /**
     * Check whether a change to the existing text layout requires a
     * new view layout.
     */
    private void checkForResize() {
        boolean sizeChanged = false;

        if (mLayout != null) {
            LayoutParams mLayoutParams = getLayoutParams(); //jec+
            // Check if our width changed
            if (mLayoutParams.width == LayoutParams.WRAP_CONTENT) {
                sizeChanged = true;
                invalidate();
            }

            // Check if our height changed
            if (mLayoutParams.height == LayoutParams.WRAP_CONTENT) {
                int desiredHeight = getDesiredHeight();

                if (desiredHeight != this.getHeight()) {
                    sizeChanged = true;
                }
            } else if (mLayoutParams.height == LayoutParams.MATCH_PARENT) {
                if (mDesiredHeightAtMeasure >= 0) {
                    int desiredHeight = getDesiredHeight();

                    if (desiredHeight != mDesiredHeightAtMeasure) {
                        sizeChanged = true;
                    }
                }
            }
        }

        if (sizeChanged) {
            requestLayout();
            // caller will have already invalidated
        }
    }

    /**
     * Check whether entirely new text requires a new view layout
     * or merely a new text layout.
     */
    private void checkForRelayout() {
        // If we have a fixed width, we can just swap in a new text layout
        // if the text height stays the same or if the view height is fixed.
        LayoutParams mLayoutParams = getLayoutParams(); //jec+
        if ((mLayoutParams.width != LayoutParams.WRAP_CONTENT ||
                (mMaxWidthMode == mMinWidthMode && mMaxWidth == mMinWidth)) &&
                (mHint == null || mHintLayout != null) &&
                (getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight() > 0)) {
            // Static width, so try making a new text layout.

            int oldht = mLayout.getHeight();
            int want = mLayout.getWidth();
            int hintWant = mHintLayout == null ? 0 : mHintLayout.getWidth();

            /*
             * No need to bring the text into view, since the size is not
             * changing (unless we do the requestLayout(), in which case it
             * will happen at measure).
             */
            makeNewLayout(want, hintWant, UNKNOWN_BORING, UNKNOWN_BORING,
                    getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight(),
                    false);

//            if (mEllipsize != TruncateAt.MARQUEE) {
//                // In a fixed-height view, so use our new text layout.
//                if (mLayoutParams.height != LayoutParams.WRAP_CONTENT &&
//                    mLayoutParams.height != LayoutParams.MATCH_PARENT) {
//                    invalidate();
//                    return;
//                }
//
//                // Dynamic height, but height has stayed the same,
//                // so use our new text layout.
//                if (mLayout.getHeight() == oldht &&
//                    (mHintLayout == null || mHintLayout.getHeight() == oldht)) {
//                    invalidate();
//                    return;
//                }
//            }

            // We lose: the height has changed and we have a dynamic height.
            // Request a new view layout using our new text layout.
            requestLayout();
            invalidate();
        } else {
            // Dynamic width, so we have no choice but to request a new
            // view layout with a new text layout.
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mDeferScroll >= 0) {
            int curs = mDeferScroll;
            mDeferScroll = -1;
            bringPointIntoView(Math.min(curs, mText.length()));
        }
    }

    private boolean isShowingHint() {
        return TextUtils.isEmpty(mText) && !TextUtils.isEmpty(mHint);
    }

    /**
     * Returns true if anything changed.
     */
    private boolean bringTextIntoView() {
        Layout layout = isShowingHint() ? mHintLayout : mLayout;
        int line = 0;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            line = layout.getLineCount() - 1;
        }

        Layout.Alignment a = layout.getParagraphAlignment(line);
        int dir = layout.getParagraphDirection(line);
        int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int ht = layout.getHeight();

        int scrollx, scrolly;

        // Convert to left, center, or right alignment.
        if (a == Layout.Alignment.ALIGN_NORMAL) {
            a = dir == Layout.DIR_LEFT_TO_RIGHT ? Layout.Alignment.ALIGN_LEFT :
                    Layout.Alignment.ALIGN_RIGHT;
        } else if (a == Layout.Alignment.ALIGN_OPPOSITE) {
            a = dir == Layout.DIR_LEFT_TO_RIGHT ? Layout.Alignment.ALIGN_RIGHT :
                    Layout.Alignment.ALIGN_LEFT;
        }

        if (a == Layout.Alignment.ALIGN_CENTER) {
            /*
             * Keep centered if possible, or, if it is too wide to fit,
             * keep leading edge in view.
             */

            int left = (int) Math.floor(layout.getLineLeft(line));
            int right = (int) Math.ceil(layout.getLineRight(line));

            if (right - left < hspace) {
                scrollx = (right + left) / 2 - hspace / 2;
            } else {
                if (dir < 0) {
                    scrollx = right - hspace;
                } else {
                    scrollx = left;
                }
            }
        } else if (a == Layout.Alignment.ALIGN_RIGHT) {
            int right = (int) Math.ceil(layout.getLineRight(line));
            scrollx = right - hspace;
        } else { // a == Layout.Alignment.ALIGN_LEFT (will also be the default)
            scrollx = (int) Math.floor(layout.getLineLeft(line));
        }

        if (ht < vspace) {
            scrolly = 0;
        } else {
            if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                scrolly = ht - vspace;
            } else {
                scrolly = 0;
            }
        }

        if (scrollx != getScrollX() || scrolly != getScrollY()) {
            scrollTo(scrollx, scrolly);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Move the point, specified by the offset, into the view if it is needed.
     * This has to be called after layout. Returns true if anything changed.
     */
    public boolean bringPointIntoView(int offset) {
        if (isLayoutRequested()) {
            mDeferScroll = offset;
            return false;
        }
        boolean changed = false;

        Layout layout = isShowingHint() ? mHintLayout : mLayout;

        if (layout == null) return changed;

        int line = layout.getLineForOffset(offset);

        int grav;

        switch (layout.getParagraphAlignment(line)) {
            case ALIGN_LEFT:
                grav = 1;
                break;
            case ALIGN_RIGHT:
                grav = -1;
                break;
            case ALIGN_NORMAL:
                grav = layout.getParagraphDirection(line);
                break;
            case ALIGN_OPPOSITE:
                grav = -layout.getParagraphDirection(line);
                break;
            case ALIGN_CENTER:
            default:
                grav = 0;
                break;
        }

        // We only want to clamp the cursor to fit within the layout width
        // in left-to-right modes, because in a right to left alignment,
        // we want to scroll to keep the line-right on the screen, as other
        // lines are likely to have text flush with the right margin, which
        // we want to keep visible.
        // A better long-term solution would probably be to measure both
        // the full line and a blank-trimmed version, and, for example, use
        // the latter measurement for centering and right alignment, but for
        // the time being we only implement the cursor clamping in left to
        // right where it is most likely to be annoying.
        final boolean clamped = grav > 0;
        // FIXME: Is it okay to truncate this, or should we round?
        final int x = (int) layout.getPrimaryHorizontal(offset, clamped);
        final int top = layout.getLineTop(line);
        final int bottom = layout.getLineTop(line + 1);

        int left = (int) Math.floor(layout.getLineLeft(line));
        int right = (int) Math.ceil(layout.getLineRight(line));
        int ht = layout.getHeight();

        int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        if (!mHorizontallyScrolling && right - left > hspace && right > x) {
            // If cursor has been clamped, make sure we don't scroll.
            right = Math.max(x, left + hspace);
        }

        int hslack = (bottom - top) / 2;
        int vslack = hslack;

        if (vslack > vspace / 4)
            vslack = vspace / 4;
        if (hslack > hspace / 4)
            hslack = hspace / 4;

        int hs = getScrollX();
        int vs = getScrollY();

        if (top - vs < vslack)
            vs = top - vslack;
        if (bottom - vs > vspace - vslack)
            vs = bottom - (vspace - vslack);
        if (ht - vs < vspace)
            vs = ht - vspace;
        if (0 - vs > 0)
            vs = 0;

        if (grav != 0) {
            if (x - hs < hslack) {
                hs = x - hslack;
            }
            if (x - hs > hspace - hslack) {
                hs = x - (hspace - hslack);
            }
        }

        if (grav < 0) {
            if (left - hs > 0)
                hs = left;
            if (right - hs < hspace)
                hs = right - hspace;
        } else if (grav > 0) {
            if (right - hs < hspace)
                hs = right - hspace;
            if (left - hs > 0)
                hs = left;
        } else /* grav == 0 */ {
            if (right - left <= hspace) {
                /*
                 * If the entire text fits, center it exactly.
                 */
                hs = left - (hspace - (right - left)) / 2;
            } else if (x > right - hslack) {
                /*
                 * If we are near the right edge, keep the right edge
                 * at the edge of the view.
                 */
                hs = right - hspace;
            } else if (x < left + hslack) {
                /*
                 * If we are near the left edge, keep the left edge
                 * at the edge of the view.
                 */
                hs = left;
            } else if (left > hs) {
                /*
                 * Is there whitespace visible at the left?  Fix it if so.
                 */
                hs = left;
            } else if (right < hs + hspace) {
                /*
                 * Is there whitespace visible at the right?  Fix it if so.
                 */
                hs = right - hspace;
            } else {
                /*
                 * Otherwise, float as needed.
                 */
                if (x - hs < hslack) {
                    hs = x - hslack;
                }
                if (x - hs > hspace - hslack) {
                    hs = x - (hspace - hslack);
                }
            }
        }

        if (hs != getScrollX() || vs != getScrollY()) {
            if (mScroller == null) {
                scrollTo(hs, vs);
            } else {
                long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
                int dx = hs - getScrollX();
                int dy = vs - getScrollY();

                if (duration > ANIMATED_SCROLL_GAP) {
                    mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
                    awakenScrollBars(mScroller.getDuration());
                    invalidate();
                } else {
                    if (!mScroller.isFinished()) {
                        mScroller.abortAnimation();
                    }

                    scrollBy(dx, dy);
                }

                mLastScroll = AnimationUtils.currentAnimationTimeMillis();
            }

            changed = true;
        }

        if (isFocused()) {
            // This offsets because getInterestingRect() is in terms of viewport coordinates, but
            // requestRectangleOnScreen() is in terms of content coordinates.

            // The offsets here are to ensure the rectangle we are using is
            // within our view bounds, in case the cursor is on the far left
            // or right.  If it isn't withing the bounds, then this request
            // will be ignored.
            if (mTempRect == null) mTempRect = new Rect();
            mTempRect.set(x - 2, top, x + 2, bottom);
            getInterestingRect(mTempRect, line);
            mTempRect.offset(getScrollX(), getScrollY());

            if (requestRectangleOnScreen(mTempRect)) {
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Move the cursor, if needed, so that it is at an offset that is visible
     * to the user.  This will not move the cursor if it represents more than
     * one character (a selection range).  This will only work if the
     * TextView contains spannable text; otherwise it will do nothing.
     *
     * @return True if the cursor was actually moved, false otherwise.
     */
    public boolean moveCursorToVisibleOffset() {
        if (!(mText instanceof Spannable)) {
            return false;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start != end) {
            return false;
        }

        // First: make sure the line is visible on screen:

        int line = mLayout.getLineForOffset(start);

        final int top = mLayout.getLineTop(line);
        final int bottom = mLayout.getLineTop(line + 1);
        final int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int vslack = (bottom - top) / 2;
        if (vslack > vspace / 4)
            vslack = vspace / 4;
        final int vs = getScrollY();

        if (top < (vs + vslack)) {
            line = mLayout.getLineForVertical(vs + vslack + (bottom - top));
        } else if (bottom > (vspace + vs - vslack)) {
            line = mLayout.getLineForVertical(vspace + vs - vslack - (bottom - top));
        }

        // Next: make sure the character is visible on screen:

        final int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        final int hs = getScrollX();
        final int leftChar = mLayout.getOffsetForHorizontal(line, hs);
        final int rightChar = mLayout.getOffsetForHorizontal(line, hspace + hs);

        // line might contain bidirectional text
        final int lowChar = leftChar < rightChar ? leftChar : rightChar;
        final int highChar = leftChar > rightChar ? leftChar : rightChar;

        int newStart = start;
        if (newStart < lowChar) {
            newStart = lowChar;
        } else if (newStart > highChar) {
            newStart = highChar;
        }

        if (newStart != start) {
            Selection.setSelection((Spannable) mText, newStart);
            return true;
        }

        return false;
    }

    @Override
    public void computeScroll() {
        if (mScroller != null) {
            if (mScroller.computeScrollOffset()) {
                setScrollX(mScroller.getCurrX());
                setScrollY(mScroller.getCurrY());
                ViewCompat.invalidateParentCaches(this);
                postInvalidate();  // So we draw again
            }
        }
    }

    private void getInterestingRect(Rect r, int line) {
        convertFromViewportToContentCoordinates(r);

        // Rectangle can can be expanded on first and last line to take
        // padding into account.
        // TODO Take left/right padding into account too?
        if (line == 0) r.top -= getExtendedPaddingTop();
        if (line == mLayout.getLineCount() - 1) r.bottom += getExtendedPaddingBottom();
    }

    private void convertFromViewportToContentCoordinates(Rect r) {
        final int horizontalOffset = viewportToContentHorizontalOffset();
        r.left += horizontalOffset;
        r.right += horizontalOffset;

        final int verticalOffset = viewportToContentVerticalOffset();
        r.top += verticalOffset;
        r.bottom += verticalOffset;
    }

    int viewportToContentHorizontalOffset() {
        return getCompoundPaddingLeft() - getScrollX();
    }

    int viewportToContentVerticalOffset() {
        int offset = getExtendedPaddingTop() - getScrollY();
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            offset += getVerticalOffset(false);
        }
        return offset;
    }

    /**
     * Convenience for {@link android.text.Selection#getSelectionStart}.
     */
    public int getSelectionStart() {
        return Selection.getSelectionStart(getText());
    }

    /**
     * Convenience for {@link android.text.Selection#getSelectionEnd}.
     */
    public int getSelectionEnd() {
        return Selection.getSelectionEnd(getText());
    }

    /**
     * Return true iff there is a selection inside this text view.
     */
    public boolean hasSelection() {
        final int selectionStart = getSelectionStart();
        final int selectionEnd = getSelectionEnd();

        return selectionStart >= 0 && selectionStart != selectionEnd;
    }

    /**
     * Sets the properties of this field (lines, horizontally scrolling,
     * transformation method) to be for a single-line input.
     *
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public void setSingleLine() {
        setSingleLine(true);
    }

    /**
     * Sets the properties of this field to transform input to ALL CAPS
     * display. This may use a "small caps" formatting if available.
     * This setting will be ignored if this field is editable or selectable.
     * <p>
     * This call replaces the current transformation method. Disabling this
     * will not necessarily restore the previous behavior from before this
     * was enabled.
     *
     * @attr ref android.R.styleable#TextView_textAllCaps
     * @see #setTransformationMethod(TransformationMethod)
     */
    public void setAllCaps(boolean allCaps) {
        if (allCaps) {
//            setTransformationMethod(new AllCapsTransformationMethod(getContext()));
        } else {
            setTransformationMethod(null);
        }
    }

    /**
     * Adds or remove the EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE on the mInputType.
     *
     * @param singleLine
     */
    private void setInputTypeSingleLine(boolean singleLine) {
        if (mEditor != null &&
                (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
            if (singleLine) {
                mEditor.mInputType &= ~EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
            } else {
                mEditor.mInputType |= EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
            }
        }
    }

    private void applySingleLine(boolean singleLine, boolean applyTransformation,
                                 boolean changeMaxLines) {
        mSingleLine = singleLine;
        if (singleLine) {
            setLines(1);
            setHorizontallyScrolling(true);
            if (applyTransformation) {
                setTransformationMethod(SingleLineTransformationMethod.getInstance());
            }
        } else {
            if (changeMaxLines) {
                setMaxLines(Integer.MAX_VALUE);
            }
            setHorizontallyScrolling(false);
            if (applyTransformation) {
                setTransformationMethod(null);
            }
        }
    }

    /**
     * Gets the number of times the marquee animation is repeated. Only meaningful if the
     * TextView has marquee enabled.
     *
     * @return the number of times the marquee animation is repeated. -1 if the animation
     * repeats indefinitely
     * @attr ref android.R.styleable#TextView_marqueeRepeatLimit
     * @see #setMarqueeRepeatLimit(int)
     */
    public int getMarqueeRepeatLimit() {
        return mMarqueeRepeatLimit;
    }

    /**
     * Sets how many times to repeat the marquee animation. Only applied if the
     * TextView has marquee enabled. Set to -1 to repeat indefinitely.
     *
     * @attr ref android.R.styleable#TextView_marqueeRepeatLimit
     * @see #getMarqueeRepeatLimit()
     */
    public void setMarqueeRepeatLimit(int marqueeLimit) {
        mMarqueeRepeatLimit = marqueeLimit;
    }

    /**
     * Set the TextView so that when it takes focus, all the text is
     * selected.
     *
     * @attr ref android.R.styleable#TextView_selectAllOnFocus
     */
    public void setSelectAllOnFocus(boolean selectAllOnFocus) {
        createEditorIfNeeded();
        mEditor.mSelectAllOnFocus = selectAllOnFocus;

        if (selectAllOnFocus && !(mText instanceof Spannable)) {
            setText(mText, BufferType.SPANNABLE);
        }
    }

    /**
     * @return whether or not the cursor is visible (assuming this TextView is editable)
     * @attr ref android.R.styleable#TextView_cursorVisible
     * @see #setCursorVisible(boolean)
     */
    public boolean isCursorVisible() {
        // true is the default value
        return mEditor == null ? true : mEditor.mCursorVisible;
    }

    /**
     * Set whether the cursor is visible. The default is true. Note that this property only
     * makes sense for editable TextView.
     *
     * @attr ref android.R.styleable#TextView_cursorVisible
     * @see #isCursorVisible()
     */
    public void setCursorVisible(boolean visible) {
        if (visible && mEditor == null) return; // visible is the default value with no edit data
        createEditorIfNeeded();
        if (mEditor.mCursorVisible != visible) {
            mEditor.mCursorVisible = visible;
            invalidate();

            mEditor.makeBlink();

            // InsertionPointCursorController depends on mCursorVisible
            mEditor.prepareCursorControllers();
        }
    }

    private boolean canMarquee() {
//        int width = (getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight());
//        return width > 0 && (mLayout.getLineWidth(0) > width ||
//                (mMarqueeFadeMode != MARQUEE_FADE_NORMAL && mSavedMarqueeModeLayout != null &&
//                        mSavedMarqueeModeLayout.getLineWidth(0) > width));
        return false;
    }

    private void startMarquee() {
        // Do not ellipsize EditText
        if (getKeyListener() != null) return;

        if (compressText(getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight())) {
            return;
        }

        if ((mMarquee == null || mMarquee.isStopped()) && (isFocused() || isSelected()) &&
                getLineCount() == 1 && canMarquee()) {

            if (mMarqueeFadeMode == MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS) {
                mMarqueeFadeMode = MARQUEE_FADE_SWITCH_SHOW_FADE;
                final Layout tmp = mLayout;
                mLayout = mSavedMarqueeModeLayout;
                mSavedMarqueeModeLayout = tmp;
                setHorizontalFadingEdgeEnabled(true);
                requestLayout();
                invalidate();
            }

            if (mMarquee == null) mMarquee = new Marquee(this);
            mMarquee.start(mMarqueeRepeatLimit);
        }
    }

    private void stopMarquee() {
        if (mMarquee != null && !mMarquee.isStopped()) {
            mMarquee.stop();
        }

        if (mMarqueeFadeMode == MARQUEE_FADE_SWITCH_SHOW_FADE) {
            mMarqueeFadeMode = MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS;
            final Layout tmp = mSavedMarqueeModeLayout;
            mSavedMarqueeModeLayout = mLayout;
            mLayout = tmp;
            setHorizontalFadingEdgeEnabled(false);
            requestLayout();
            invalidate();
        }
    }

    private void startStopMarquee(boolean start) {
    }

    /**
     * This method is called when the text is changed, in case any subclasses
     * would like to know.
     * <p>
     * Within <code>text</code>, the <code>lengthAfter</code> characters
     * beginning at <code>start</code> have just replaced old text that had
     * length <code>lengthBefore</code>. It is an error to attempt to make
     * changes to <code>text</code> from this callback.
     *
     * @param text         The text the TextView is displaying
     * @param start        The offset of the start of the range of the text that was
     *                     modified
     * @param lengthBefore The length of the former text that has been replaced
     * @param lengthAfter  The length of the replacement modified text
     */
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        // intentionally empty, template pattern method can be overridden by subclasses
    }

    /**
     * This method is called when the selection has changed, in case any
     * subclasses would like to know.
     *
     * @param selStart The new selection start location.
     * @param selEnd   The new selection end location.
     */
    protected void onSelectionChanged(int selStart, int selEnd) {
//        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }

    /**
     * Adds a TextWatcher to the list of those whose methods are called
     * whenever this TextView's text changes.
     * <p>
     * In 1.0, the {@link TextWatcher#afterTextChanged} method was erroneously
     * not called after {@link #setText} calls.  Now, doing {@link #setText}
     * if there are any text changed listeners forces the buffer type to
     * Editable if it would not otherwise be and does call this method.
     */
    public void addTextChangedListener(TextWatcher watcher) {
        if (mListeners == null) {
            mListeners = new ArrayList<TextWatcher>();
        }

        mListeners.add(watcher);
    }

    /**
     * Removes the specified TextWatcher from the list of those whose
     * methods are called
     * whenever this TextView's text changes.
     */
    public void removeTextChangedListener(TextWatcher watcher) {
        if (mListeners != null) {
            int i = mListeners.indexOf(watcher);

            if (i >= 0) {
                mListeners.remove(i);
            }
        }
    }

    private void sendBeforeTextChanged(CharSequence text, int start, int before, int after) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).beforeTextChanged(text, start, before, after);
            }
        }

        // The spans that are inside or intersect the modified region no longer make sense
//        removeIntersectingNonAdjacentSpans(start, start + before, SpellCheckSpan.class);
        removeIntersectingNonAdjacentSpans(start, start + before, SuggestionSpan.class);
    }

    // Removes all spans that are inside or actually overlap the start..end range
    private <T> void removeIntersectingNonAdjacentSpans(int start, int end, Class<T> type) {
        if (!(mText instanceof Editable)) return;
        Editable text = (Editable) mText;

        T[] spans = text.getSpans(start, end, type);
        final int length = spans.length;
        for (int i = 0; i < length; i++) {
            final int spanStart = text.getSpanStart(spans[i]);
            final int spanEnd = text.getSpanEnd(spans[i]);
            if (spanEnd == start || spanStart == end) break;
            text.removeSpan(spans[i]);
        }
    }

    void removeAdjacentSuggestionSpans(final int pos) {
        if (!(mText instanceof Editable)) return;
        final Editable text = (Editable) mText;

        final SuggestionSpan[] spans = text.getSpans(pos, pos, SuggestionSpan.class);
        final int length = spans.length;
        for (int i = 0; i < length; i++) {
            final int spanStart = text.getSpanStart(spans[i]);
            final int spanEnd = text.getSpanEnd(spans[i]);
            if (spanEnd == pos || spanStart == pos) {
//                if (SpellChecker.haveWordBoundariesChanged(text, pos, pos, spanStart, spanEnd)) {
//                    text.removeSpan(spans[i]);
//                }
            }
        }
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void sendOnTextChanged(CharSequence text, int start, int before, int after) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).onTextChanged(text, start, before, after);
            }
        }

        if (mEditor != null) mEditor.sendOnTextChanged(start, after);
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void sendAfterTextChanged(Editable text) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).afterTextChanged(text);
            }
        }
        hideErrorIfUnchanged();
    }

    void updateAfterEdit() {
        invalidate();
        int curs = getSelectionStart();

        if (curs >= 0 || (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            registerForPreDraw();
        }

        checkForResize();

        if (curs >= 0) {
            mHighlightPathBogus = true;
            if (mEditor != null) mEditor.makeBlink();
            bringPointIntoView(curs);
        }
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void handleTextChanged(CharSequence buffer, int start, int before, int after) {
        final Editor.InputMethodState ims = mEditor == null ? null : mEditor.mInputMethodState;
        if (ims == null || ims.mBatchEditNesting == 0) {
            updateAfterEdit();
        }
        if (ims != null) {
            ims.mContentChanged = true;
            if (ims.mChangedStart < 0) {
                ims.mChangedStart = start;
                ims.mChangedEnd = start + before;
            } else {
                ims.mChangedStart = Math.min(ims.mChangedStart, start);
                ims.mChangedEnd = Math.max(ims.mChangedEnd, start + before - ims.mChangedDelta);
            }
            ims.mChangedDelta += after - before;
        }
        resetErrorChangedFlag();
        sendOnTextChanged(buffer, start, before, after);
        onTextChanged(buffer, start, before, after);
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void spanChange(Spanned buf, Object what, int oldStart, int newStart, int oldEnd, int newEnd) {
        // XXX Make the start and end move together if this ends up
        // spending too much time invalidating.

        boolean selChanged = false;
        int newSelStart = -1, newSelEnd = -1;

        final Editor.InputMethodState ims = mEditor == null ? null : mEditor.mInputMethodState;

        if (what == Selection.SELECTION_END) {
            selChanged = true;
            newSelEnd = newStart;

            if (oldStart >= 0 || newStart >= 0) {
                invalidateCursor(Selection.getSelectionStart(buf), oldStart, newStart);
                checkForResize();
                registerForPreDraw();
                if (mEditor != null) mEditor.makeBlink();
            }
        }

        if (what == Selection.SELECTION_START) {
            selChanged = true;
            newSelStart = newStart;

            if (oldStart >= 0 || newStart >= 0) {
                int end = Selection.getSelectionEnd(buf);
                invalidateCursor(end, oldStart, newStart);
            }
        }

        if (selChanged) {
            mHighlightPathBogus = true;
            if (mEditor != null && !isFocused()) mEditor.mSelectionMoved = true;

            if ((buf.getSpanFlags(what) & Spanned.SPAN_INTERMEDIATE) == 0) {
                if (newSelStart < 0) {
                    newSelStart = Selection.getSelectionStart(buf);
                }
                if (newSelEnd < 0) {
                    newSelEnd = Selection.getSelectionEnd(buf);
                }
                onSelectionChanged(newSelStart, newSelEnd);
            }
        }

        if (what instanceof UpdateAppearance || what instanceof ParagraphStyle ||
                what instanceof CharacterStyle) {
            if (ims == null || ims.mBatchEditNesting == 0) {
                invalidate();
                mHighlightPathBogus = true;
                checkForResize();
            } else {
                ims.mContentChanged = true;
            }
            if (mEditor != null) {
                if (oldStart >= 0) mEditor.invalidateTextDisplayList(mLayout, oldStart, oldEnd);
                if (newStart >= 0) mEditor.invalidateTextDisplayList(mLayout, newStart, newEnd);
            }
        }

        if (MetaKeyKeyListener.isMetaTracker(buf, what)) {
            mHighlightPathBogus = true;
            if (ims != null && MetaKeyKeyListener.isSelectingMetaTracker(buf, what)) {
                ims.mSelectionModeChanged = true;
            }

            if (Selection.getSelectionStart(buf) >= 0) {
                if (ims == null || ims.mBatchEditNesting == 0) {
                    invalidateCursor();
                } else {
                    ims.mCursorChanged = true;
                }
            }
        }

        if (what instanceof ParcelableSpan) {
            // If this is a span that can be sent to a remote process,
            // the current extract editor would be interested in it.
            if (ims != null && ims.mExtractedTextRequest != null) {
                if (ims.mBatchEditNesting != 0) {
                    if (oldStart >= 0) {
                        if (ims.mChangedStart > oldStart) {
                            ims.mChangedStart = oldStart;
                        }
                        if (ims.mChangedStart > oldEnd) {
                            ims.mChangedStart = oldEnd;
                        }
                    }
                    if (newStart >= 0) {
                        if (ims.mChangedStart > newStart) {
                            ims.mChangedStart = newStart;
                        }
                        if (ims.mChangedStart > newEnd) {
                            ims.mChangedStart = newEnd;
                        }
                    }
                } else {
                    if (DEBUG_EXTRACT) Log.v(LOG_TAG, "Span change outside of batch: "
                            + oldStart + "-" + oldEnd + ","
                            + newStart + "-" + newEnd + " " + what);
                    ims.mContentChanged = true;
                }
            }
        }

    }

    /**
     * public void dispatchFinishTemporaryDetach() { onFinishTemporaryDetach(); }
     */
//    @Override
    public void dispatchFinishTemporaryDetach() {
        super.dispatchFinishTemporaryDetach();
        mDispatchTemporaryDetach = true;
//        super.dispatchFinishTemporaryDetach();
        onFinishTemporaryDetach();
        mDispatchTemporaryDetach = false;
    }

    @Override
    public void onStartTemporaryDetach() {
        super.onStartTemporaryDetach();
        // Only track when onStartTemporaryDetach() is called directly,
        // usually because this instance is an editable field in a list
        if (!mDispatchTemporaryDetach) mTemporaryDetach = true;

        // Tell the editor that we are temporarily detached. It can use this to preserve
        // selection state as needed.
        if (mEditor != null) mEditor.mTemporaryDetach = true;
    }

    @Override
    public void onFinishTemporaryDetach() {
        super.onFinishTemporaryDetach();
        // Only track when onStartTemporaryDetach() is called directly,
        // usually because this instance is an editable field in a list
        if (!mDispatchTemporaryDetach) mTemporaryDetach = false;
        if (mEditor != null) mEditor.mTemporaryDetach = false;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mTemporaryDetach) {
            // If we are temporarily in the detach state, then do nothing.
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
            return;
        }

        if (mEditor != null) mEditor.onFocusChanged(focused, direction);

        if (focused) {
            if (mText instanceof Spannable) {
                Spannable sp = (Spannable) mText;
                MetaKeyKeyListener.resetMetaState(sp);
            }
        }

        startStopMarquee(focused);

        if (mTransformation != null) {
            mTransformation.onFocusChanged(this, mText, focused, direction, previouslyFocusedRect);
        }

        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        if (mEditor != null) mEditor.onWindowFocusChanged(hasWindowFocus);

        startStopMarquee(hasWindowFocus);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (mEditor != null && visibility != VISIBLE) {
            mEditor.hideControllers();
        }
    }

    /**
     * Use {@link BaseInputConnection#removeComposingSpans
     * BaseInputConnection.removeComposingSpans()} to remove any IME composing
     * state from this text view.
     */
    public void clearComposingText() {
        if (mText instanceof Spannable) {
            BaseInputConnection.removeComposingSpans((Spannable) mText);
        }
    }

    @Override
    public void setSelected(boolean selected) {
        boolean wasSelected = isSelected();

        super.setSelected(selected);

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();

        if (mEditor != null) mEditor.onTouchEvent(event);

        final boolean superResult = super.onTouchEvent(event);

        /*
         * Don't handle the release after a long press, because it will
         * move the selection away from whatever the menu action was
         * trying to affect.
         */
        if (mEditor != null && mEditor.mDiscardNextActionUp && action == MotionEvent.ACTION_UP) {
            mEditor.mDiscardNextActionUp = false;
            return superResult;
        }

        boolean focused = isFocused();
        final boolean touchIsFinished = (action == MotionEvent.ACTION_UP) &&
                (mEditor == null || !mEditor.mIgnoreActionUpEvent) && focused;

        if ((mMovement != null || onCheckIsTextEditor()) && isEnabled()
                && mText instanceof Spannable && mLayout != null) {
            boolean handled = false;

            if (mMovement != null) {
                handled |= mMovement.onTouchEvent(this, (Spannable) mText, event);
            }

            final boolean textIsSelectable = isTextSelectable();
            if (touchIsFinished && mLinksClickable && mAutoLinkMask != 0 && textIsSelectable) {
                // The LinkMovementMethod which should handle taps on links has not been installed
                // on non editable text that support text selection.
                // We reproduce its behavior here to open links for these.
                ClickableSpan[] links = ((Spannable) mText).getSpans(getSelectionStart(),
                        getSelectionEnd(), ClickableSpan.class);

                if (links.length > 0) {
                    links[0].onClick(this);
                    handled = true;
                }
            }

            if (touchIsFinished && (isTextEditable() || textIsSelectable)) {
                // Show the IME, except when selecting in read-only text.
                final InputMethodManager imm = InputMethodManagerCompat.peekInstance();
                viewClicked(imm);
                if (!textIsSelectable && mEditor.mShowSoftInputOnFocus) {
                    handled |= imm != null && imm.showSoftInput(this, 0);
                }

                // The above condition ensures that the mEditor is not null
                mEditor.onTouchUpEvent(event);

                handled = true;
            }

            if (handled) {
                return true;
            }
        }

        return superResult;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mMovement != null && mText instanceof Spannable && mLayout != null) {
            try {
                if (mMovement.onGenericMotionEvent(this, (Spannable) mText, event)) {
                    return true;
                }
            } catch (AbstractMethodError ex) {
                // onGenericMotionEvent was added to the MovementMethod interface in API 12.
                // Ignore its absence in case third party applications implemented the
                // interface directly.
            }
        }
        return super.onGenericMotionEvent(event);
    }

    /**
     * @return True iff this TextView contains a text that can be edited, or if this is
     * a selectable TextView.
     */
    boolean isTextEditable() {
        return mText instanceof Editable && onCheckIsTextEditor() && isEnabled();
    }

    /**
     * Returns true, only while processing a touch gesture, if the initial
     * touch down event caused focus to move to the text view and as a result
     * its selection changed.  Only valid while processing the touch gesture
     * of interest, in an editable text view.
     */
    public boolean didTouchFocusSelect() {
        return mEditor != null && mEditor.mTouchFocusSelected;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        if (mEditor != null) mEditor.mIgnoreActionUpEvent = true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if (mMovement != null && mText instanceof Spannable && mLayout != null) {
            if (mMovement.onTrackballEvent(this, (Spannable) mText, event)) {
                return true;
            }
        }

        return super.onTrackballEvent(event);
    }

    public void setScroller(Scroller s) {
        mScroller = s;
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        return super.getLeftFadingEdgeStrength();
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        return super.getRightFadingEdgeStrength();
    }

    @Override
    protected int computeHorizontalScrollRange() {
        if (mLayout != null) {
            return mSingleLine && (mGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT ?
                    (int) mLayout.getLineWidth(0) : mLayout.getWidth();
        }

        return super.computeHorizontalScrollRange();
    }

    @Override
    protected int computeVerticalScrollRange() {
        if (mLayout != null)
            return mLayout.getHeight();

        return super.computeVerticalScrollRange();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return getHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
    }

    @Override
    public void findViewsWithText(ArrayList<View> outViews, CharSequence searched, int flags) {
        super.findViewsWithText(outViews, searched, flags);
        if (!outViews.contains(this) && (flags & FIND_VIEWS_WITH_TEXT) != 0
                && !TextUtils.isEmpty(searched) && !TextUtils.isEmpty(mText)) {
            String searchedLowerCase = searched.toString().toLowerCase();
            String textLowerCase = mText.toString().toLowerCase();
            if (textLowerCase.contains(searchedLowerCase)) {
                outViews.add(this);
            }
        }
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        final int filteredMetaState = event.getMetaState() & ~KeyEvent.META_CTRL_MASK;
        if (KeyEvent.metaStateHasNoModifiers(filteredMetaState)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    if (canSelectText()) {
                        return onTextContextMenuItem(ID_SELECT_ALL);
                    }
                    break;
                case KeyEvent.KEYCODE_X:
                    if (canCut()) {
                        return onTextContextMenuItem(ID_CUT);
                    }
                    break;
                case KeyEvent.KEYCODE_C:
                    if (canCopy()) {
                        return onTextContextMenuItem(ID_COPY);
                    }
                    break;
                case KeyEvent.KEYCODE_V:
                    if (canPaste()) {
                        return onTextContextMenuItem(ID_PASTE);
                    }
                    break;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    /**
     * Unlike {@link #textCanBeSelected()}, this method is based on the <i>current</i> state of the
     * TextView. {@link #textCanBeSelected()} has to be true (this is one of the conditions to have
     * a selection controller (see {@link Editor#prepareCursorControllers()}), but this is not
     * sufficient.
     */
    protected boolean canSelectText() {
        return mText.length() != 0 && mEditor != null && mEditor.hasSelectionController();
    }

    /**
     * Test based on the <i>intrinsic</i> charateristics of the TextView.
     * The text must be spannable and the movement method must allow for arbitary selection.
     * <p>
     * See also {@link #canSelectText()}.
     */
    boolean textCanBeSelected() {
        // prepareCursorController() relies on this method.
        // If you change this condition, make sure prepareCursorController is called anywhere
        // the value of this condition might be changed.
        if (mMovement == null || !mMovement.canSelectArbitrarily()) return false;
        return isTextEditable() ||
                (isTextSelectable() && mText instanceof Spannable && isEnabled());
    }

    private Locale getTextServicesLocale(boolean allowNullLocale) {
        // Start fetching the text services locale asynchronously.
        updateTextServicesLocaleAsync();
        // If !allowNullLocale and there is no cached text services locale, just return the default
        // locale.
        return (mCurrentSpellCheckerLocaleCache == null && !allowNullLocale) ? Locale.getDefault()
                : mCurrentSpellCheckerLocaleCache;
    }

    /**
     * This is a temporary method. Future versions may support multi-locale text.
     * Caveat: This method may not return the latest text services locale, but this should be
     * acceptable and it's more important to make this method asynchronous.
     *
     * @return The locale that should be used for a word iterator
     * in this TextView, based on the current spell checker settings,
     * the current IME's locale, or the system default locale.
     * Please note that a word iterator in this TextView is different from another word iterator
     * used by SpellChecker.java of TextView. This method should be used for the former.
     * @hide
     */
    // TODO: Support multi-locale
    // TODO: Update the text services locale immediately after the keyboard locale is switched
    // by catching intent of keyboard switch event
    public Locale getTextServicesLocale() {
        return getTextServicesLocale(false /* allowNullLocale */);
    }

    /**
     * This is a temporary method. Future versions may support multi-locale text.
     * Caveat: This method may not return the latest spell checker locale, but this should be
     * acceptable and it's more important to make this method asynchronous.
     *
     * @return The locale that should be used for a spell checker in this TextView,
     * based on the current spell checker settings, the current IME's locale, or the system default
     * locale.
     * @hide
     */
    public Locale getSpellCheckerLocale() {
        return getTextServicesLocale(true /* allowNullLocale */);
    }

    private void updateTextServicesLocaleAsync() {
        // AsyncTask.execute() uses a serial executor which means we don't have
        // to lock around updateTextServicesLocaleLocked() to prevent it from
        // being executed n times in parallel.
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                updateTextServicesLocaleLocked();
            }
        });
    }

    private void updateTextServicesLocaleLocked() {
//        final TextServicesManager textServicesManager = (TextServicesManager)
//                mContext.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
//        final SpellCheckerSubtype subtype = textServicesManager.getCurrentSpellCheckerSubtype(true);
//        final Locale locale;
//        if (subtype != null) {
//            locale = SpellCheckerSubtype.constructLocaleFromString(subtype.getLocale());
//        } else {
//            locale = null;
//        }
//        mCurrentSpellCheckerLocaleCache = locale;
        mCurrentSpellCheckerLocaleCache = null;
    }

    void onLocaleChanged() {
        // Will be re-created on demand in getWordIterator with the proper new locale
        mEditor.mWordIterator = null;
    }

    /**
     * This method is used by the ArrowKeyMovementMethod to jump from one word to the other.
     * Made available to achieve a consistent behavior.
     *
     * @hide
     */
    public WordIterator getWordIterator() {
        if (mEditor != null) {
            return mEditor.getWordIterator();
        } else {
            return null;
        }
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);

        final boolean isPassword = hasPasswordTransformationMethod();
        if (!isPassword || shouldSpeakPasswordsForAccessibility()) {
            final CharSequence text = getTextForAccessibility();
            if (!TextUtils.isEmpty(text)) {
                event.getText().add(text);
            }
        }
    }

    /**
     * @return true if the user has explicitly allowed accessibility services
     * to speak passwords.
     */
    private boolean shouldSpeakPasswordsForAccessibility() {
        return (Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD, 0) == 1);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);

        event.setClassName(BaseEditorView.class.getName());
        final boolean isPassword = hasPasswordTransformationMethod();
        event.setPassword(isPassword);

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            event.setFromIndex(Selection.getSelectionStart(mText));
            event.setToIndex(Selection.getSelectionEnd(mText));
            event.setItemCount(mText.length());
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.setClassName(BaseEditorView.class.getName());
        final boolean isPassword = hasPasswordTransformationMethod();
        info.setPassword(isPassword);

        if (!isPassword || shouldSpeakPasswordsForAccessibility()) {
            info.setText(getTextForAccessibility());
        }

//        if (mBufferType == BufferType.EDITABLE) {
//            info.setEditable(true);
//        }
//
//        if (mEditor != null) {
//            info.setInputType(mEditor.mInputType);
//
//            if (mEditor.mError != null) {
//                info.setContentInvalid(true);
//                info.setError(mEditor.mError);
//            }
//        }

        if (!TextUtils.isEmpty(mText)) {
            info.addAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
            info.addAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
            info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE);
        }

        if (isFocused()) {
            if (canSelectText()) {
                info.addAction(AccessibilityNodeInfo.ACTION_SET_SELECTION);
            }
            if (canCopy()) {
                info.addAction(AccessibilityNodeInfo.ACTION_COPY);
            }
            if (canPaste()) {
                info.addAction(AccessibilityNodeInfo.ACTION_PASTE);
            }
            if (canCut()) {
                info.addAction(AccessibilityNodeInfo.ACTION_CUT);
            }
        }

    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_CLICK: {
                boolean handled = false;

                // Simulate View.onTouchEvent for an ACTION_UP event.
                if (isClickable() || isLongClickable()) {
                    if (isFocusable() && !isFocused()) {
                        requestFocus();
                    }

                    performClick();
                    handled = true;
                }

                // Simulate TextView.onTouchEvent for an ACTION_UP event.
                if ((mMovement != null || onCheckIsTextEditor()) && isEnabled()
                        && mText instanceof Spannable && mLayout != null
                        && (isTextEditable() || isTextSelectable()) && isFocused()) {
                    // Show the IME, except when selecting in read-only text.
                    final InputMethodManager imm = InputMethodManagerCompat.peekInstance();
                    viewClicked(imm);
                    if (!isTextSelectable() && mEditor.mShowSoftInputOnFocus && imm != null) {
                        handled |= imm.showSoftInput(this, 0);
                    }
                }

                return handled;
            }
            case AccessibilityNodeInfo.ACTION_COPY: {
                if (isFocused() && canCopy()) {
                    if (onTextContextMenuItem(ID_COPY)) {
                        return true;
                    }
                }
            }
            return false;
            case AccessibilityNodeInfo.ACTION_PASTE: {
                if (isFocused() && canPaste()) {
                    if (onTextContextMenuItem(ID_PASTE)) {
                        return true;
                    }
                }
            }
            return false;
            case AccessibilityNodeInfo.ACTION_CUT: {
                if (isFocused() && canCut()) {
                    if (onTextContextMenuItem(ID_CUT)) {
                        return true;
                    }
                }
            }
            return false;
            case AccessibilityNodeInfo.ACTION_SET_SELECTION: {
                if (isFocused() && canSelectText()) {
                    CharSequence text = getIterableTextForAccessibility();
                    if (text == null) {
                        return false;
                    }
                    final int start = (arguments != null) ? arguments.getInt(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, -1) : -1;
                    final int end = (arguments != null) ? arguments.getInt(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, -1) : -1;
                    if ((getSelectionStart() != start || getSelectionEnd() != end)) {
                        // No arguments clears the selection.
                        if (start == end && end == -1) {
                            Selection.removeSelection((Spannable) text);
                            return true;
                        }
                        if (start >= 0 && start <= end && end <= text.length()) {
                            Selection.setSelection((Spannable) text, start, end);
                            // Make sure selection mode is engaged.
                            if (mEditor != null) {
                                mEditor.startSelectionActionMode();
                            }
                            return true;
                        }
                    }
                }
            }
            return false;
            default: {
                return super.performAccessibilityAction(action, arguments);
            }
        }
    }

    @Override
    public void sendAccessibilityEvent(int eventType) {
        // Do not send scroll events since first they are not interesting for
        // accessibility and second such events a generated too frequently.
        // For details see the implementation of bringTextIntoView().
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return;
        }
        super.sendAccessibilityEvent(eventType);
    }

    /**
     * Gets the text reported for accessibility purposes.
     *
     * @return The accessibility text.
     * @hide
     */
    public CharSequence getTextForAccessibility() {
        CharSequence text = getText();
        if (TextUtils.isEmpty(text)) {
            text = getHint();
        }
        return text;
    }

    void sendAccessibilityEventTypeViewTextChanged(CharSequence beforeText,
                                                   int fromIndex, int removedCount, int addedCount) {
        AccessibilityEvent event =
                AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        event.setFromIndex(fromIndex);
        event.setRemovedCount(removedCount);
        event.setAddedCount(addedCount);
        event.setBeforeText(beforeText);
        sendAccessibilityEventUnchecked(event);
    }

    /**
     * Returns whether this text view is a current input method target.  The
     * default implementation just checks with {@link InputMethodManager}.
     */
    public boolean isInputMethodTarget() {
        InputMethodManager imm = InputMethodManagerCompat.peekInstance();
        return imm != null && imm.isActive(this);
    }

    /**
     * Called when a context menu option for the text view is selected.  Currently
     * this will be one of {@link android.R.id#selectAll}, {@link android.R.id#cut},
     * {@link android.R.id#copy} or {@link android.R.id#paste}.
     *
     * @return true if the context menu item action was performed.
     */
    public boolean onTextContextMenuItem(int id) {
        int min = 0;
        int max = mText.length();

//        if (isFocused()) {  //在弹出菜单，可能已经失去了焦点
        final int selStart = getSelectionStart();
        final int selEnd = getSelectionEnd();

        min = Math.max(0, Math.min(selStart, selEnd));
        max = Math.max(0, Math.max(selStart, selEnd));
//        }

        switch (id) {
            case ID_SELECT_ALL:
                // This does not enter text selection mode. Text is highlighted, so that it can be
                // bulk edited, like selectAllOnFocus does. Returns true even if text is empty.
                selectAllText();
                return true;

            case ID_PASTE:
                paste(min, max);
                return true;

            case ID_CUT:
                setPrimaryClip(ClipData.newPlainText(null, getTransformedText(min, max)));
                deleteText_internal(min, max);
                stopSelectionActionMode();
                return true;

            case ID_COPY:
                setPrimaryClip(ClipData.newPlainText(null, getTransformedText(min, max)));
                stopSelectionActionMode();
                return true;
        }
        return false;
    }

    CharSequence getTransformedText(int start, int end) {
        return removeSuggestionSpans(mTransformed.subSequence(start, end));
    }

    @Override
    public boolean performLongClick() {
        boolean handled = false;

        if (super.performLongClick()) {
            handled = true;
        }

        if (mEditor != null) {
            handled |= mEditor.performLongClick(handled);
        }

        if (handled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (mEditor != null) mEditor.mDiscardNextActionUp = true;
        }

        return handled;
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        if (mEditor != null) {
            mEditor.onScrollChanged();
        }
    }

    /**
     * Return whether or not suggestions are enabled on this TextView. The suggestions are generated
     * by the IME or by the spell checker as the user types. This is done by adding
     * {@link SuggestionSpan}s to the text.
     * <p>
     * When suggestions are enabled (default), this list of suggestions will be displayed when the
     * user asks for them on these parts of the text. This value depends on the inputType of this
     * TextView.
     * <p>
     * The class of the input type must be {@link InputType#TYPE_CLASS_TEXT}.
     * <p>
     * In addition, the type variation must be one of
     * {@link InputType#TYPE_TEXT_VARIATION_NORMAL},
     * {@link InputType#TYPE_TEXT_VARIATION_EMAIL_SUBJECT},
     * {@link InputType#TYPE_TEXT_VARIATION_LONG_MESSAGE},
     * {@link InputType#TYPE_TEXT_VARIATION_SHORT_MESSAGE} or
     * {@link InputType#TYPE_TEXT_VARIATION_WEB_EDIT_TEXT}.
     * <p>
     * And finally, the {@link InputType#TYPE_TEXT_FLAG_NO_SUGGESTIONS} flag must <i>not</i> be set.
     *
     * @return true if the suggestions popup window is enabled, based on the inputType.
     */
    public boolean isSuggestionsEnabled() {
        if (mEditor == null) return false;
        if ((mEditor.mInputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        if ((mEditor.mInputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) > 0) return false;

        final int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
        return (variation == EditorInfo.TYPE_TEXT_VARIATION_NORMAL ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_LONG_MESSAGE ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);
    }

    /**
     * Retrieves the value set in {@link #setCustomSelectionActionModeCallback}. Default is null.
     *
     * @return The current custom selection callback.
     */
    public ActionMode.Callback getCustomSelectionActionModeCallback() {
        return mEditor == null ? null : mEditor.mCustomSelectionActionModeCallback;
    }

    /**
     * If provided, this ActionMode.Callback will be used to create the ActionMode when text
     * selection is initiated in this View.
     * <p>
     * The standard implementation populates the menu with a subset of Select All, Cut, Copy and
     * Paste actions, depending on what this View supports.
     * <p>
     * A custom implementation can add new entries in the default menu in its
     * {@link ActionMode.Callback#onPrepareActionMode(ActionMode, android.view.Menu)} method. The
     * default actions can also be removed from the menu using {@link android.view.Menu#removeItem(int)} and
     * passing {@link android.R.id#selectAll}, {@link android.R.id#cut}, {@link android.R.id#copy}
     * or {@link android.R.id#paste} ids as parameters.
     * <p>
     * Returning false from
     * {@link ActionMode.Callback#onCreateActionMode(ActionMode, android.view.Menu)} will prevent
     * the action mode from being started.
     * <p>
     * Action click events should be handled by the custom implementation of
     * {@link ActionMode.Callback#onActionItemClicked(ActionMode, android.view.MenuItem)}.
     * <p>
     * Note that text selection mode is not started when a TextView receives focus and the
     * {@link android.R.attr#selectAllOnFocus} flag has been set. The content is highlighted in
     * that case, to allow for quick replacement.
     */
    public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
        createEditorIfNeeded();
        mEditor.mCustomSelectionActionModeCallback = actionModeCallback;
    }

    /**
     * @hide
     */
    protected void stopSelectionActionMode() {
        mEditor.stopSelectionActionMode();
    }

    boolean canCut() {
        if (hasPasswordTransformationMethod()) {
            return false;
        }

        if (mText.length() > 0 && hasSelection() && mText instanceof Editable && mEditor != null &&
                mEditor.mKeyListener != null) {
            return true;
        }

        return false;
    }

    boolean canCopy() {
        if (hasPasswordTransformationMethod()) {
            return false;
        }

        if (mText.length() > 0 && hasSelection() && mEditor != null) {
            return true;
        }

        return false;
    }

    boolean canPaste() {
        return (mText instanceof Editable &&
                mEditor != null && mEditor.mKeyListener != null &&
                getSelectionStart() >= 0 &&
                getSelectionEnd() >= 0 &&
                ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE)).
                        hasPrimaryClip());
    }

    boolean selectAllText() {
        final int length = mText.length();
        Selection.setSelection((Spannable) mText, 0, length);
        return length > 0;
    }

    /**
     * Paste clipboard content between min and max positions.
     */
    private void paste(int min, int max) {
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {
            boolean didFirst = false;
            for (int i = 0; i < clip.getItemCount(); i++) {
                CharSequence paste = clip.getItemAt(i).coerceToStyledText(getContext());
                if (paste != null) {
                    if (!didFirst) {
                        Selection.setSelection((Spannable) mText, max);
                        ((Editable) mText).replace(min, max, paste);
                        didFirst = true;
                    } else {
                        ((Editable) mText).insert(getSelectionEnd(), "\n");
                        ((Editable) mText).insert(getSelectionEnd(), paste);
                    }
                }
            }
            stopSelectionActionMode();
            LAST_CUT_OR_COPY_TIME = 0;
        }
    }

    private void setPrimaryClip(ClipData clip) {
        ClipboardManager clipboard = (ClipboardManager) getContext().
                getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(clip);
        LAST_CUT_OR_COPY_TIME = SystemClock.uptimeMillis();
    }

    /**
     * Get the character offset closest to the specified absolute position. A typical use case is to
     * pass the result of {@link MotionEvent#getX()} and {@link MotionEvent#getY()} to this method.
     *
     * @param x The horizontal absolute position of a point on screen
     * @param y The vertical absolute position of a point on screen
     * @return the character offset for the character whose position is closest to the specified
     * position. Returns -1 if there is no layout.
     */
    public int getOffsetForPosition(float x, float y) {
        if (getLayout() == null) return -1;
        final int line = getLineAtCoordinate(y);
        final int offset = getOffsetAtCoordinate(line, x);
        return offset;
    }

    float convertToLocalHorizontalCoordinate(float x) {
        x -= getTotalPaddingLeft();
        // Clamp the position to inside of the view.
        x = Math.max(0.0f, x);
        x = Math.min(getWidth() - getTotalPaddingRight() - 1, x);
        x += getScrollX();
        return x;
    }

    int getLineAtCoordinate(float y) {
        y -= getTotalPaddingTop();
        // Clamp the position to inside of the view.
        y = Math.max(0.0f, y);
        y = Math.min(getHeight() - getTotalPaddingBottom() - 1, y);
        y += getScrollY();
        return getLayout().getLineForVertical((int) y);
    }

    private int getOffsetAtCoordinate(int line, float x) {
        x = convertToLocalHorizontalCoordinate(x);
        return getLayout().getOffsetForHorizontal(line, x);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return mEditor != null && mEditor.hasInsertionController();

            case DragEvent.ACTION_DRAG_ENTERED:
                BaseEditorView.this.requestFocus();
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                final int offset = getOffsetForPosition(event.getX(), event.getY());
                Selection.setSelection((Spannable) mText, offset);
                return true;

            case DragEvent.ACTION_DROP:
                if (mEditor != null) mEditor.onDrop(event);
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
            case DragEvent.ACTION_DRAG_EXITED:
            default:
                return true;
        }
    }

    boolean isInBatchEditMode() {
        if (mEditor == null) return false;
        final Editor.InputMethodState ims = mEditor.mInputMethodState;
        if (ims != null) {
            return ims.mBatchEditNesting > 0;
        }
        return mEditor.mInBatchEditControllers;
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        mTextDir = getTextDirectionHeuristic();

        if (mLayout != null) {
            checkForRelayout();
        }
    }

    TextDirectionHeuristic getTextDirectionHeuristic() {
        if (hasPasswordTransformationMethod()) {
            // passwords fields should be LTR
            return TextDirectionHeuristics.LTR;
        }

        // Always need to resolve layout direction first
        final boolean defaultIsRtl = (getLayoutDirection() == LAYOUT_DIRECTION_RTL);

        // Now, we can select the heuristic
        switch (getTextDirection()) {
            default:
            case TEXT_DIRECTION_FIRST_STRONG:
                return (defaultIsRtl ? TextDirectionHeuristics.FIRSTSTRONG_RTL :
                        TextDirectionHeuristics.FIRSTSTRONG_LTR);
            case TEXT_DIRECTION_ANY_RTL:
                return TextDirectionHeuristics.ANYRTL_LTR;
            case TEXT_DIRECTION_LTR:
                return TextDirectionHeuristics.LTR;
            case TEXT_DIRECTION_RTL:
                return TextDirectionHeuristics.RTL;
            case TEXT_DIRECTION_LOCALE:
                return TextDirectionHeuristics.LOCALE;
        }
    }


    /**
     * @hide
     */
    protected void viewClicked(InputMethodManager imm) {
        if (imm != null) {
            imm.viewClicked(this);
        }
    }

    /**
     * Deletes the range of text [start, end[.
     *
     * @hide
     */
    protected void deleteText_internal(int start, int end) {
        ((Editable) mText).delete(start, end);
    }

    /**
     * Replaces the range of text [start, end[ by replacement text
     *
     * @hide
     */
    protected void replaceText_internal(int start, int end, CharSequence text) {
        ((Editable) mText).replace(start, end, text);
    }

    /**
     * Sets a span on the specified range of text
     *
     * @hide
     */
    protected void setSpan_internal(Object span, int start, int end, int flags) {
        ((Editable) mText).setSpan(span, start, end, flags);
    }

    /**
     * Moves the cursor to the specified offset position in text
     *
     * @hide
     */
    protected void setCursorPosition_internal(int start, int end) {
        Selection.setSelection(((Editable) mText), start, end);
    }

    /**
     * An Editor should be created as soon as any of the editable-specific fields (grouped
     * inside the Editor object) is assigned to a non-default value.
     * This method will create the Editor if needed.
     * <p>
     * A standard TextView (as well as buttons, checkboxes...) should not qualify and hence will
     * have a null Editor, unlike an EditText. Inconsistent in-between states will have an
     * Editor for backward compatibility, as soon as one of these fields is assigned.
     * <p>
     * Also note that for performance reasons, the mEditor is created when needed, but not
     * reset when no more edit-specific fields are needed.
     */
    private void createEditorIfNeeded() {
        if (mEditor == null) {
            mEditor = new Editor(this);
        }
    }

    /**
     * @hide
     */
//    @Override
    public CharSequence getIterableTextForAccessibility() {
        if (!(mText instanceof Spannable)) {
            setText(mText, BufferType.SPANNABLE);
        }
        return mText;
    }

    private void init() {
        // 横屏的时候关闭完成按钮和编辑状态不使用系统的全屏编辑框
        setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        pref = layoutContext.pref = Pref.getInstance(getContext());
        pref.registerOnSharedPreferenceChangeListener(this);

        Paint lineNumberPaint = new Paint();
        lineNumberPaint.setColor(Color.BLACK);
        lineNumberPaint.setTypeface(getPaint().getTypeface());
        lineNumberPaint.setTextSize(getPaint().getTextSize() / 2);
        lineNumberPaint.setTextAlign(Paint.Align.RIGHT);
        lineNumberPaint.setAntiAlias(true);
        layoutContext.lineNumberPaint = lineNumberPaint;

        Paint linePaint = new Paint();
        linePaint.setColor(layoutContext.lineNumberPaint.getColor());
        linePaint.setAntiAlias(false);
        linePaint.setStyle(Paint.Style.STROKE);
        layoutContext.linePaint = linePaint;

        Paint gutterBackgroundPaint = new Paint();
        gutterBackgroundPaint.setColor(Color.LTGRAY);
        layoutContext.gutterBackgroundPaint = gutterBackgroundPaint;
        //默认有1行
        setLineNumber(1);

        //===== White Space Start =======================================
        onTextSizeChanged();

        //===== White Space End =======================================

        initTheme();

        onSharedPreferenceChanged(null, Pref.KEY_FONT_SIZE);
        onSharedPreferenceChanged(null, Pref.KEY_CURSOR_WIDTH);
        onSharedPreferenceChanged(null, Pref.KEY_SHOW_LINE_NUMBER);
        onSharedPreferenceChanged(null, Pref.KEY_WORD_WRAP);
        onSharedPreferenceChanged(null, Pref.KEY_SHOW_WHITESPACE);
        onSharedPreferenceChanged(null, Pref.KEY_TAB_SIZE);
        onSharedPreferenceChanged(null, Pref.KEY_AUTO_INDENT);
        onSharedPreferenceChanged(null, Pref.KEY_AUTO_CAPITALIZE);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case Pref.KEY_FONT_SIZE:
                setTextSize(TypedValue.COMPLEX_UNIT_SP, pref.getFontSize());
                break;
            case Pref.KEY_CURSOR_WIDTH:
                mHighlightPathBogus = true;
                layoutContext.cursorThickness = pref.getCursorThickness();
                break;
            case Pref.KEY_SHOW_LINE_NUMBER:
                setLineNumber(layoutContext.lineNumber);
                break;
            case Pref.KEY_WORD_WRAP:
                setHorizontallyScrolling(!pref.isWordWrap());
                break;
            case Pref.KEY_SHOW_WHITESPACE:
                layoutContext.isShowWhiteSpace = pref.isShowWhiteSpace();
                break;
            case Pref.KEY_TAB_SIZE:
                updateTabChar();
                break;
            case Pref.KEY_AUTO_INDENT:
                if (mText != null && mText instanceof SpannableStringBuilder) {
                    ((SpannableStringBuilder) mText).setAutoIndent(pref.isAutoIndent());
                }
                break;
            case Pref.KEY_AUTO_CAPITALIZE:
                if (mEditor != null) {
                    if (!pref.isAutoCapitalize()) {
                        mEditor.mInputType &= ~EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES;
                    } else {
                        mEditor.mInputType |= EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES;
                    }
                }
                break;
        }
    }

    private void onTextSizeChanged() {
        updateTabChar();
    }

    private void updateTabChar() {
        TextPaint tp = mTextPaint;

        float spaceWidth = tp.measureText(" ");
        float tabWidth = spaceWidth * (pref == null ? 4 : pref.getTabSize());

        Layout.TAB_INCREMENT = (int) tabWidth;
    }

    public void setLineNumber(int lineNumber) {
        layoutContext.lineNumber = lineNumber;

        if (!layoutContext.pref.isShowLineNumber()) {
            //还原填充
            setPaddingRelative(getPaddingEnd(), getPaddingTop(), getPaddingEnd(), getPaddingBottom());
            return;
        }

        int gutterPadding = SysUtils.dpAsPixels(getContext(), 8);
        //预留2位空间，避免过多设置padding导致卡
        layoutContext.gutterWidth = (int) layoutContext.lineNumberPaint.measureText(Integer.toString(lineNumber * 10));
        layoutContext.gutterWidth += gutterPadding;
        layoutContext.lineNumberX = layoutContext.gutterWidth - SysUtils.dpAsPixels(getContext(), 4);
        if (getPaddingLeft() != layoutContext.gutterWidth + gutterPadding) {
            setPaddingRelative(layoutContext.gutterWidth + gutterPadding, getPaddingTop(), getPaddingEnd(), getPaddingBottom());
        }
    }

    private void drawLineNumber(Canvas canvas) {
        if (!layoutContext.pref.isShowLineNumber())
            return;
        int width = layoutContext.gutterWidth + getScrollX();
        int height = getScrollY() + getHeight();
        canvas.drawRect(getScrollX(), getScrollY(), width, height, layoutContext.gutterBackgroundPaint);
        canvas.drawLine(width, getScrollY(), width, height, layoutContext.linePaint);

        List<TextLineNumber.LineInfo> lines = layoutContext.textLineNumber.getLines();
        for (TextLineNumber.LineInfo line : lines) {
//            canvas.restore();
//            canvas.translate(layoutContext.scrollX, 0);
            canvas.drawText(line.text, layoutContext.lineNumberX + layoutContext.scrollX, line.y, layoutContext.lineNumberPaint);
//            canvas.translate(-layoutContext.scrollX, 0);
//            canvas.save();
//            canvas.translate(layoutContext.translateX, layoutContext.translateY);
        }
    }

    public int getMaxScrollY() {
        if (getLayout() == null)
            return 0;
        int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        return getLayout().getHeight() - vspace;
    }

    @SuppressWarnings("ResourceType")
    public void initTheme() {
        TypedArray a = getContext().obtainStyledAttributes(new int[]{
                R.attr.textForeground,
                R.attr.textBackground,
                R.attr.gutterForeground,
                R.attr.gutterBackground,
                R.attr.gutterDivider,
                R.attr.invisibles,
                R.attr.selection,
        });
        int textForeground = a.getColor(0, Color.BLACK);
        int textBackground = a.getColor(1, Color.BLACK);
        int gutterForeground = a.getColor(2, Color.BLACK);
        int gutterBackground = a.getColor(3, Color.BLACK);
        int gutterDivider = a.getColor(4, Color.BLACK);
        int invisibles = a.getColor(5, Color.BLACK);
        int selection = a.getColor(6, Color.BLACK);
        a.recycle();

        setBackgroundColor(textBackground);
        setTextColor(textForeground);
        setHighlightColor(selection);

        layoutContext.lineNumberPaint.setColor(gutterForeground);
        layoutContext.linePaint.setColor(gutterDivider);
        layoutContext.gutterBackgroundPaint.setColor(gutterBackground);
        layoutContext.whiteSpaceColor = invisibles;
    }

    public enum BufferType {
        NORMAL, SPANNABLE, EDITABLE,
    }

    /**
     * Interface definition for a callback to be invoked when an action is
     * performed on the editor.
     */
    public interface OnEditorActionListener {
        /**
         * Called when an action is being performed.
         *
         * @param v        The view that was clicked.
         * @param actionId Identifier of the action.  This will be either the
         *                 identifier you supplied, or {@link EditorInfo#IME_NULL
         *                 EditorInfo.IME_NULL} if being called due to the enter key
         *                 being pressed.
         * @param event    If triggered by an enter key, this is the event;
         *                 otherwise, this is null.
         * @return Return true if you have consumed the action, else false.
         */
        boolean onEditorAction(BaseEditorView v, int actionId, KeyEvent event);
    }

    /**
     * User interface state that is stored by TextView for implementing
     * {@link View#onSaveInstanceState}.
     */
    public static class SavedState extends BaseSavedState {
        @SuppressWarnings("hiding")
        public static final Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
        private static final String TAG = "SavedState";
        int selStart;
        int selEnd;
        //        CharSequence text;
        char[] text;
        int textLength;
        boolean frozenWithFocus;
        CharSequence error;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            if (DLog.DEBUG) DLog.d(TAG, "SavedState() called with: in = [" + in + "]");

            selStart = in.readInt();
            selEnd = in.readInt();
            frozenWithFocus = (in.readInt() != 0);
//            text = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
//            text = in.createCharArray();
            textLength = in.readInt();

            if (in.readInt() != 0) {
                error = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            if (DLog.DEBUG)
                DLog.d(TAG, "writeToParcel() called with: out = [" + out + "], flags = [" + flags + "]");
            out.writeInt(selStart);
            out.writeInt(selEnd);
            out.writeInt(frozenWithFocus ? 1 : 0);
//            TextUtils.writeToParcel(text, out, flags);
//            out.writeCharArray(this.text);
            out.writeInt(this.textLength);

            if (error == null) {
                out.writeInt(0);
            } else {
                out.writeInt(1);
                TextUtils.writeToParcel(error, out, flags);
            }
        }

        @Override
        public String toString() {
            String str = "TextView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " start=" + selStart + " end=" + selEnd;
            if (text != null) {
                str += " text=" + text;
            }
            return str + "}";
        }
    }

    private static final class Marquee {
        // TODO: Add an option to configure this
        private static final float MARQUEE_DELTA_MAX = 0.07f;
        private static final int MARQUEE_DELAY = 1200;
        private static final int MARQUEE_RESTART_DELAY = 1200;
        private static final int MARQUEE_DP_PER_SECOND = 30;

        private static final byte MARQUEE_STOPPED = 0x0;
        private static final byte MARQUEE_STARTING = 0x1;
        private static final byte MARQUEE_RUNNING = 0x2;

        private final WeakReference<BaseEditorView> mView;
        private final Choreographer mChoreographer;
        private final float mPixelsPerSecond;
        private byte mStatus = MARQUEE_STOPPED;
        private float mMaxScroll;
        private float mMaxFadeScroll;
        private float mGhostStart;
        private float mGhostOffset;
        private float mFadeStop;
        private int mRepeatLimit;

        private float mScroll;
        private long mLastAnimationMs;
        private Choreographer.FrameCallback mTickCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                tick();
            }
        };
        private Choreographer.FrameCallback mStartCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                mStatus = MARQUEE_RUNNING;
//                mLastAnimationMs = mChoreographer.getFrameTime();
                tick();
            }
        };
        private Choreographer.FrameCallback mRestartCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (mStatus == MARQUEE_RUNNING) {
                    if (mRepeatLimit >= 0) {
                        mRepeatLimit--;
                    }
                    start(mRepeatLimit);
                }
            }
        };

        Marquee(BaseEditorView v) {
            final float density = v.getContext().getResources().getDisplayMetrics().density;
            mPixelsPerSecond = MARQUEE_DP_PER_SECOND * density;
            mView = new WeakReference<BaseEditorView>(v);
            mChoreographer = Choreographer.getInstance();
        }

        void tick() {
            if (mStatus != MARQUEE_RUNNING) {
                return;
            }

            mChoreographer.removeFrameCallback(mTickCallback);

//            final TextView textView = mView.get();
//            if (textView != null && (textView.isFocused() || textView.isSelected())) {
//                long currentMs = mChoreographer.getFrameTime();
//                long deltaMs = currentMs - mLastAnimationMs;
//                mLastAnimationMs = currentMs;
//                float deltaPx = deltaMs / 1000f * mPixelsPerSecond;
//                mScroll += deltaPx;
//                if (mScroll > mMaxScroll) {
//                    mScroll = mMaxScroll;
//                    mChoreographer.postFrameCallbackDelayed(mRestartCallback, MARQUEE_DELAY);
//                } else {
//                    mChoreographer.postFrameCallback(mTickCallback);
//                }
//                textView.invalidate();
//            }
        }

        void stop() {
            mStatus = MARQUEE_STOPPED;
            mChoreographer.removeFrameCallback(mStartCallback);
            mChoreographer.removeFrameCallback(mRestartCallback);
            mChoreographer.removeFrameCallback(mTickCallback);
            resetScroll();
        }

        private void resetScroll() {
            mScroll = 0.0f;
            final BaseEditorView textView = mView.get();
            if (textView != null) textView.invalidate();
        }

        void start(int repeatLimit) {
            if (repeatLimit == 0) {
                stop();
                return;
            }
            mRepeatLimit = repeatLimit;
            final BaseEditorView textView = mView.get();
            if (textView != null && textView.mLayout != null) {
                mStatus = MARQUEE_STARTING;
                mScroll = 0.0f;
                final int textWidth = textView.getWidth() - textView.getCompoundPaddingLeft() -
                        textView.getCompoundPaddingRight();
                final float lineWidth = textView.mLayout.getLineWidth(0);
                final float gap = textWidth / 3.0f;
                mGhostStart = lineWidth - textWidth + gap;
                mMaxScroll = mGhostStart + textWidth;
                mGhostOffset = lineWidth + gap;
                mFadeStop = lineWidth + textWidth / 6.0f;
                mMaxFadeScroll = mGhostStart + lineWidth + lineWidth;

                textView.invalidate();
                mChoreographer.postFrameCallback(mStartCallback);
            }
        }

        float getGhostOffset() {
            return mGhostOffset;
        }

        float getScroll() {
            return mScroll;
        }

        float getMaxFadeScroll() {
            return mMaxFadeScroll;
        }

        boolean shouldDrawLeftFade() {
            return mScroll <= mFadeStop;
        }

        boolean shouldDrawGhost() {
            return mStatus == MARQUEE_RUNNING && mScroll > mGhostStart;
        }

        boolean isRunning() {
            return mStatus == MARQUEE_RUNNING;
        }

        boolean isStopped() {
            return mStatus == MARQUEE_STOPPED;
        }
    }

    public static class ChangeWatcher implements TextWatcher, SpanWatcher {
        private WeakReference<BaseEditorView> textView;
//        private CharSequence mBeforeText;

        public ChangeWatcher(BaseEditorView textView) {
            this.textView = new WeakReference<>(textView);
        }

        public void beforeTextChanged(CharSequence buffer, int start,
                                      int before, int after) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "beforeTextChanged start=" + start
                    + " before=" + before + " after=" + after + ": " + buffer);

            BaseEditorView tv = textView.get();
            if (tv == null) return;
            tv.sendBeforeTextChanged(buffer, start, before, after);
        }

        public void onTextChanged(CharSequence buffer, int start, int before, int after) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "onTextChanged start=" + start
                    + " before=" + before + " after=" + after + ": " + buffer);
            BaseEditorView tv = textView.get();
            if (tv == null) return;
            tv.handleTextChanged(buffer, start, before, after);

        }

        public void afterTextChanged(Editable buffer) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "afterTextChanged: " + buffer);
            BaseEditorView tv = textView.get();
            if (tv == null) return;

            tv.sendAfterTextChanged(buffer);

            if (MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListenerCompat.META_SELECTING) != 0) {
                MetaKeyKeyListenerCompat.stopSelecting(tv, buffer);
            }
        }

        public void onSpanChanged(Spannable buf, Object what, int s, int e, int st, int en) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "onSpanChanged s=" + s + " e=" + e
                    + " st=" + st + " en=" + en + " what=" + what + ": " + buf);
            BaseEditorView tv = textView.get();
            if (tv == null) return;

            tv.spanChange(buf, what, s, st, e, en);
        }

        public void onSpanAdded(Spannable buf, Object what, int s, int e) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "onSpanAdded s=" + s + " e=" + e
                    + " what=" + what + ": " + buf);
            BaseEditorView tv = textView.get();
            if (tv == null) return;

            tv.spanChange(buf, what, -1, s, -1, e);
        }

        public void onSpanRemoved(Spannable buf, Object what, int s, int e) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "onSpanRemoved s=" + s + " e=" + e
                    + " what=" + what + ": " + buf);
            BaseEditorView tv = textView.get();
            if (tv == null) return;

            tv.spanChange(buf, what, s, -1, e, -1);
        }
    }

}
