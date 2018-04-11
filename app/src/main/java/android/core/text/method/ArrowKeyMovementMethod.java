/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.core.text.method;

import android.core.text.Layout;
import android.core.text.MetaKeyKeyListenerCompat;
import android.core.text.Selection;
import android.core.widget.BaseEditorView;
import android.graphics.Rect;
import android.os.Build;
import android.text.Spannable;
import android.text.method.MetaKeyKeyListener;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * A movement method that provides cursor movement and selection.
 * Supports displaying the context menu on DPad Center.
 */
public class ArrowKeyMovementMethod extends BaseMovementMethod implements MovementMethod {
    private static final Object LAST_TAP_DOWN = new Object();
    private static ArrowKeyMovementMethod sInstance;

    private static boolean isSelecting(Spannable buffer) {
        return ((MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SHIFT_ON) == 1) ||
                (MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListenerCompat.META_SELECTING) != 0));
    }

    private static int getCurrentLineTop(Spannable buffer, Layout layout) {
        return layout.getLineTop(layout.getLineForOffset(Selection.getSelectionEnd(buffer)));
    }

    private static int getPageHeight(BaseEditorView widget) {
        // This calculation does not take into account the view transformations that
        // may have been applied to the child or its containers.  In case of scaling or
        // rotation, the calculated page height may be incorrect.
        final Rect rect = new Rect();
        return widget.getGlobalVisibleRect(rect) ? rect.height() : 0;
    }

    private static boolean isTouchSelecting(boolean isMouse, Spannable buffer) {
        return isMouse ? Touch.isActivelySelecting(buffer) : isSelecting(buffer);
    }

    public static MovementMethod getInstance() {
        if (sInstance == null) {
            sInstance = new ArrowKeyMovementMethod();
        }

        return sInstance;
    }

    @Override
    protected boolean handleMovementKey(BaseEditorView widget, Spannable buffer, int keyCode,
                                        int movementMetaState, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && event.getRepeatCount() == 0
                            && MetaKeyKeyListenerCompat.getMetaState2(buffer,
                            MetaKeyKeyListenerCompat.META_SELECTING, event) != 0) {
                        return widget.showContextMenu();
                    }
                }
                break;
        }
        return super.handleMovementKey(widget, buffer, keyCode, movementMetaState, event);
    }

    @Override
    protected boolean left(BaseEditorView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendLeft(buffer, layout);
        } else {
            return Selection.moveLeft(buffer, layout);
        }
    }

    @Override
    protected boolean right(BaseEditorView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendRight(buffer, layout);
        } else {
            return Selection.moveRight(buffer, layout);
        }
    }

    @Override
    protected boolean up(BaseEditorView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendUp(buffer, layout);
        } else {
            return Selection.moveUp(buffer, layout);
        }
    }

    @Override
    protected boolean down(BaseEditorView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendDown(buffer, layout);
        } else {
            return Selection.moveDown(buffer, layout);
        }
    }

    @Override
    protected boolean pageUp(BaseEditorView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        final boolean selecting = isSelecting(buffer);
        final int targetY = getCurrentLineTop(buffer, layout) - getPageHeight(widget);
        boolean handled = false;
        for (; ; ) {
            final int previousSelectionEnd = Selection.getSelectionEnd(buffer);
            if (selecting) {
                Selection.extendUp(buffer, layout);
            } else {
                Selection.moveUp(buffer, layout);
            }
            if (Selection.getSelectionEnd(buffer) == previousSelectionEnd) {
                break;
            }
            handled = true;
            if (getCurrentLineTop(buffer, layout) <= targetY) {
                break;
            }
        }
        return handled;
    }

    @Override
    protected boolean pageDown(BaseEditorView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        final boolean selecting = isSelecting(buffer);
        final int targetY = getCurrentLineTop(buffer, layout) + getPageHeight(widget);
        boolean handled = false;
        for (; ; ) {
            final int previousSelectionEnd = Selection.getSelectionEnd(buffer);
            if (selecting) {
                Selection.extendDown(buffer, layout);
            } else {
                Selection.moveDown(buffer, layout);
            }
            if (Selection.getSelectionEnd(buffer) == previousSelectionEnd) {
                break;
            }
            handled = true;
            if (getCurrentLineTop(buffer, layout) >= targetY) {
                break;
            }
        }
        return handled;
    }

    @Override
    protected boolean top(BaseEditorView widget, Spannable buffer) {
        if (isSelecting(buffer)) {
            Selection.extendSelection(buffer, 0);
        } else {
            Selection.setSelection(buffer, 0);
        }
        return true;
    }

    @Override
    protected boolean bottom(BaseEditorView widget, Spannable buffer) {
        if (isSelecting(buffer)) {
            Selection.extendSelection(buffer, buffer.length());
        } else {
            Selection.setSelection(buffer, buffer.length());
        }
        return true;
    }

    @Override
    protected boolean lineStart(BaseEditorView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendToLeftEdge(buffer, layout);
        } else {
            return Selection.moveToLeftEdge(buffer, layout);
        }
    }

    @Override
    protected boolean lineEnd(BaseEditorView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendToRightEdge(buffer, layout);
        } else {
            return Selection.moveToRightEdge(buffer, layout);
        }
    }

    /**
     * {@hide}
     */
    @Override
    protected boolean leftWord(BaseEditorView widget, Spannable buffer) {
        final int selectionEnd = widget.getSelectionEnd();
        final WordIterator wordIterator = widget.getWordIterator();
        wordIterator.setCharSequence(buffer, selectionEnd, selectionEnd);
        return Selection.moveToPreceding(buffer, wordIterator, isSelecting(buffer));
    }

    /**
     * {@hide}
     */
    @Override
    protected boolean rightWord(BaseEditorView widget, Spannable buffer) {
        final int selectionEnd = widget.getSelectionEnd();
        final WordIterator wordIterator = widget.getWordIterator();
        wordIterator.setCharSequence(buffer, selectionEnd, selectionEnd);
        return Selection.moveToFollowing(buffer, wordIterator, isSelecting(buffer));
    }

    @Override
    protected boolean home(BaseEditorView widget, Spannable buffer) {
        return lineStart(widget, buffer);
    }

    @Override
    protected boolean end(BaseEditorView widget, Spannable buffer) {
        return lineEnd(widget, buffer);
    }

    @Override
    public boolean onTouchEvent(BaseEditorView widget, Spannable buffer, MotionEvent event) {
        int initialScrollX = -1;
        int initialScrollY = -1;
        final int action = event.getAction();
        final boolean isMouse = Build.VERSION.SDK_INT >= 18 && event.isFromSource(InputDevice.SOURCE_MOUSE);

        if (action == MotionEvent.ACTION_UP) {
            initialScrollX = Touch.getInitialScrollX(widget, buffer);
            initialScrollY = Touch.getInitialScrollY(widget, buffer);
        }

        boolean handled = Touch.onTouchEvent(widget, buffer, event);

        if (widget.isFocused() && !widget.didTouchFocusSelect()) {
            if (action == MotionEvent.ACTION_DOWN) {
                // Capture the mouse pointer down location to ensure selection starts
                // right under the mouse (and is not influenced by cursor location).
                // The code below needs to run for mouse events.
                // For touch events, the code should run only when selection is active.
                if (isMouse || isTouchSelecting(isMouse, buffer)) {
                    int offset = widget.getOffsetForPosition(event.getX(), event.getY());
                    buffer.setSpan(LAST_TAP_DOWN, offset, offset, Spannable.SPAN_POINT_POINT);
                    // Disallow intercepting of the touch events, so that
                    // users can scroll and select at the same time.
                    // without this, users would get booted out of select
                    // mode once the view detected it needed to scroll.
                    widget.getParent().requestDisallowInterceptTouchEvent(true);
                }
            } else if (action == MotionEvent.ACTION_MOVE) {

                // Cursor can be active at any location in the text while mouse pointer can start
                // selection from a totally different location. Use LAST_TAP_DOWN span to ensure
                // text selection will start from mouse pointer location.
                if (isMouse && Touch.isSelectionStarted(buffer)) {
                    int offset = buffer.getSpanStart(LAST_TAP_DOWN);
                    Selection.setSelection(buffer, offset);
                }

                if (isTouchSelecting(isMouse, buffer) && handled) {
                    // Before selecting, make sure we've moved out of the "slop".
                    // handled will be true, if we're in select mode AND we're
                    // OUT of the slop

                    // Turn long press off while we're selecting. User needs to
                    // re-tap on the selection to enable long press
                    widget.cancelLongPress();

                    // Update selection as we're moving the selection area.

                    // Get the current touch position
                    int offset = widget.getOffsetForPosition(event.getX(), event.getY());

                    Selection.extendSelection(buffer, offset);
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                // If we have scrolled, then the up shouldn't move the cursor,
                // but we do need to make sure the cursor is still visible at
                // the current scroll offset to avoid the scroll jumping later
                // to show it.
//                if ((initialScrollY >= 0 && initialScrollY != widget.getScrollY()) ||
//                    (initialScrollX >= 0 && initialScrollX != widget.getScrollX())) {
                //jec: 避免不换行时跳动
                if (!widget.getHorizontallyScrolling() && ((initialScrollY >= 0 && initialScrollY != widget.getScrollY()) ||
                        (initialScrollX >= 0 && initialScrollX != widget.getScrollX()))) {
                    widget.moveCursorToVisibleOffset();
                    return true;
                }

                int offset = widget.getOffsetForPosition(event.getX(), event.getY());
                if (isTouchSelecting(isMouse, buffer)) {
                    buffer.removeSpan(LAST_TAP_DOWN);
                    Selection.extendSelection(buffer, offset);
                }

                MetaKeyKeyListener.adjustMetaAfterKeypress(buffer);
                MetaKeyKeyListenerCompat.resetLockedMeta2(buffer);

                return true;
            }
        }
        return handled;
    }

    @Override
    public boolean canSelectArbitrarily() {
        return true;
    }

    @Override
    public void initialize(BaseEditorView widget, Spannable text) {
        Selection.setSelection(text, 0);
    }

    @Override
    public void onTakeFocus(BaseEditorView view, Spannable text, int dir) {
        if ((dir & (View.FOCUS_FORWARD | View.FOCUS_DOWN)) != 0) {
            if (view.getLayout() == null) {
                // This shouldn't be null, but do something sensible if it is.
                Selection.setSelection(text, text.length());
            }
        } else {
            Selection.setSelection(text, text.length());
        }
    }
}
