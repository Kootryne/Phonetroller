package com.phonetroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private ControllerView controllerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();
        controllerView = new ControllerView(this);
        setContentView(controllerView);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    public static class ControllerView extends View {
        private static final String PREFS = "phonetroller_profiles";
        private static final String KEY_PROFILES = "profiles_json_v2";
        private static final String OLD_KEY_PROFILES = "profiles_json";
        private static final int NO_POINTER = -1;
        private static final String TYPE_BUTTON = "button";
        private static final String TYPE_JOYSTICK = "joystick";
        private static final String TYPE_SLIDER = "slider";

        private final Context context;
        private final SharedPreferences prefs;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF temp = new RectF();
        private final List<ControllerProfile> controllers = new ArrayList<>();
        private final List<UiButton> uiButtons = new ArrayList<>();

        private int currentController = 0;
        private int selectedIndex = -1;
        private boolean editMode = false;
        private boolean snapEnabled = true;
        private float toolbarBottom = 0f;
        private int editPointerId = NO_POINTER;
        private float dragOffsetX = 0f;
        private float dragOffsetY = 0f;

        public ControllerView(Context context) {
            super(context);
            this.context = context;
            this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            setFocusable(true);
            loadControllers();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            drawBackground(canvas, w, h);
            if (editMode) {
                drawSnapGrid(canvas, w, h);
            }
            drawAllControls(canvas, w, h);
            uiButtons.clear();
            if (editMode) {
                drawEditorUi(canvas, w, h);
            } else {
                drawPlayUi(canvas, w, h);
            }
        }

        private ControllerProfile profile() {
            if (controllers.isEmpty()) createDefaults();
            currentController = Math.max(0, Math.min(currentController, controllers.size() - 1));
            return controllers.get(currentController);
        }

        private void drawBackground(Canvas canvas, int w, int h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{Color.rgb(7, 8, 15), Color.rgb(16, 18, 35), Color.rgb(5, 7, 14)},
                    null,
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }

        private void drawSnapGrid(Canvas canvas, int w, int h) {
            float step = gridPx();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.argb(35, 255, 255, 255));
            for (float x = 0; x <= w; x += step) canvas.drawLine(x, 0, x, h, paint);
            for (float y = 0; y <= h; y += step) canvas.drawLine(0, y, w, y, paint);
        }

        private void drawPlayUi(Canvas canvas, int w, int h) {
            float pad = dp(8);
            float pillH = dp(32);
            RectF profileRect = new RectF(pad, pad, Math.min(w * 0.42f, dp(260)), pad + pillH);
            drawSmallPill(canvas, profileRect, profile().name, "next_controller");

            RectF editRect = new RectF(w - dp(76), pad, w - pad, pad + pillH);
            drawSmallPill(canvas, editRect, "EDIT", "edit");
        }

        private void drawSmallPill(Canvas canvas, RectF r, String label, String action) {
            uiButtons.add(new UiButton(new RectF(r), action));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(150, 0, 0, 0));
            canvas.drawRoundRect(r, dp(16), dp(16), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(95, 150, 145, 255));
            canvas.drawRoundRect(r, dp(16), dp(16), paint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(11));
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(label, r.centerX(), r.centerY() + dp(4), textPaint);
            textPaint.setFakeBoldText(false);
        }

        private void drawEditorUi(Canvas canvas, int w, int h) {
            float pad = dp(8);
            float x = pad;
            float y = pad;
            float bh = dp(34);
            toolbarBottom = y + bh;

            String[] labels = {
                    "PLAY", profile().name, "+CTRL", "RENAME CTRL", snapEnabled ? "SNAP ON" : "SNAP OFF",
                    "+BUTTON", "+JOY", "+SLIDER", "LABEL", "DELETE", "SIZE -", "SIZE +", "LIMITS"
            };
            String[] actions = {
                    "play", "next_controller", "new_controller", "rename_controller", "toggle_snap",
                    "add_button", "add_joystick", "add_slider", "rename_control", "delete_control", "size_down", "size_up", "limits"
            };

            for (int i = 0; i < labels.length; i++) {
                float bw = buttonWidth(labels[i]);
                if (x + bw > w - pad) {
                    x = pad;
                    y += bh + dp(7);
                }
                RectF r = new RectF(x, y, x + bw, y + bh);
                drawToolbarButton(canvas, r, labels[i], actions[i], i == 0 || actions[i].equals("toggle_snap"));
                x += bw + dp(7);
                toolbarBottom = Math.max(toolbarBottom, r.bottom + dp(8));
            }

            drawSelectedInfo(canvas, w, h);
        }

        private float buttonWidth(String label) {
            return Math.max(dp(66), Math.min(dp(132), dp(34) + label.length() * dp(7)));
        }

        private void drawToolbarButton(Canvas canvas, RectF r, String label, String action, boolean accent) {
            uiButtons.add(new UiButton(new RectF(r), action));
            paint.setStyle(Paint.Style.FILL);
            int a = accent ? Color.rgb(80, 225, 190) : Color.rgb(100, 94, 235);
            int b = action.equals("delete_control") ? Color.rgb(255, 93, 128) : Color.rgb(95, 180, 255);
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, a, b, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, dp(13), dp(13), paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(85, 255, 255, 255));
            canvas.drawRoundRect(r, dp(13), dp(13), paint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(10.5f));
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(label, r.centerX(), r.centerY() + dp(4), textPaint);
            textPaint.setFakeBoldText(false);
        }

        private void drawSelectedInfo(Canvas canvas, int w, int h) {
            Control c = selectedControl();
            String text;
            if (c == null) {
                text = "Edit mode: tap a control to select it, drag it, resize it, rename it, set limits, or delete it.";
            } else {
                text = c.label + "  |  " + c.type.toUpperCase(Locale.US) + "  |  size " + Math.round(c.w * 100) + "  |  limits " + format(c.min) + " to " + format(c.max);
            }
            RectF r = new RectF(dp(12), h - dp(42), w - dp(12), h - dp(10));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(145, 0, 0, 0));
            canvas.drawRoundRect(r, dp(14), dp(14), paint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(11.5f));
            textPaint.setColor(Color.argb(235, 245, 247, 255));
            canvas.drawText(text, r.centerX(), r.centerY() + dp(4), textPaint);
        }

        private void drawAllControls(Canvas canvas, int w, int h) {
            ControllerProfile p = profile();
            if (p.controls.isEmpty()) {
                if (editMode) drawEmptyHint(canvas, w, h);
                return;
            }
            for (int i = 0; i < p.controls.size(); i++) {
                Control c = p.controls.get(i);
                boolean selected = editMode && i == selectedIndex;
                if (TYPE_JOYSTICK.equals(c.type)) drawJoystick(canvas, c, w, h, selected);
                else if (TYPE_SLIDER.equals(c.type)) drawSlider(canvas, c, w, h, selected);
                else drawButton(canvas, c, w, h, selected);
            }
        }

        private void drawEmptyHint(Canvas canvas, int w, int h) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(18));
            textPaint.setColor(Color.argb(170, 255, 255, 255));
            canvas.drawText("No controls yet. Add a button, joystick, or slider.", w / 2f, h / 2f, textPaint);
        }

        private void drawJoystick(Canvas canvas, Control c, int w, int h, boolean selected) {
            float cx = c.x * w;
            float cy = c.y * h;
            float radius = c.w * Math.min(w, h);
            float knob = radius * 0.42f;
            float kx = cx + c.joyX * radius * 0.58f;
            float ky = cy + c.joyY * radius * 0.58f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(58, 115, 115, 255));
            canvas.drawCircle(cx, cy, radius * 1.1f, paint);
            paint.setColor(Color.argb(62, 0, 0, 0));
            canvas.drawCircle(cx + dp(4), cy + dp(5), radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.argb(220, 150, 152, 255));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(70, 255, 255, 255));
            canvas.drawLine(cx - radius * 0.7f, cy, cx + radius * 0.7f, cy, paint);
            canvas.drawLine(cx, cy - radius * 0.7f, cx, cy + radius * 0.7f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(kx - knob, ky - knob, kx + knob, ky + knob,
                    Color.rgb(118, 234, 255), Color.rgb(137, 105, 255), Shader.TileMode.CLAMP));
            canvas.drawCircle(kx, ky, knob, paint);
            paint.setShader(null);

            drawControlText(canvas, c.label, cx, cy + radius + dp(22));
            if (selected) drawSelectionCircle(canvas, cx, cy, radius * 1.22f);
        }

        private void drawSlider(Canvas canvas, Control c, int w, int h, boolean selected) {
            float cx = c.x * w;
            float cy = c.y * h;
            float sw = c.w * w;
            float sh = c.h * h;
            RectF track = new RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(60, 255, 255, 255));
            canvas.drawRoundRect(track, sw / 2f, sw / 2f, paint);
            float fillTop = track.bottom - track.height() * c.value;
            RectF fill = new RectF(track.left, fillTop, track.right, track.bottom);
            paint.setShader(new LinearGradient(fill.left, fill.top, fill.right, fill.bottom,
                    Color.rgb(96, 243, 210), Color.rgb(124, 110, 255), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(fill, sw / 2f, sw / 2f, paint);
            paint.setShader(null);

            float knobY = track.bottom - track.height() * c.value;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, knobY, sw * 0.78f, paint);
            drawControlText(canvas, c.label + " " + format(c.outputValue()), cx, track.bottom + dp(24));
            if (selected) drawSelectionRect(canvas, track);
        }

        private void drawButton(Canvas canvas, Control c, int w, int h, boolean selected) {
            float cx = c.x * w;
            float cy = c.y * h;
            float radius = c.w * Math.min(w, h);
            float scale = c.pressed ? 0.9f : 1f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(68, 0, 0, 0));
            canvas.drawCircle(cx + dp(4), cy + dp(5), radius * scale, paint);
            paint.setShader(new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                    c.pressed ? Color.rgb(255, 220, 118) : Color.rgb(255, 105, 151),
                    c.pressed ? Color.rgb(108, 245, 210) : Color.rgb(128, 103, 255),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius * scale, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(110, 255, 255, 255));
            canvas.drawCircle(cx, cy, radius * scale, paint);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(Math.max(11, Math.min(22, radius / dp(3.2f)))));
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(c.label, cx, cy + dp(6), textPaint);
            textPaint.setFakeBoldText(false);
            if (selected) drawSelectionCircle(canvas, cx, cy, radius * 1.25f);
        }

        private void drawControlText(Canvas canvas, String label, float x, float y) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(12));
            textPaint.setFakeBoldText(false);
            textPaint.setColor(Color.argb(230, 235, 238, 255));
            canvas.drawText(label, x, y, textPaint);
        }

        private void drawSelectionCircle(Canvas canvas, float cx, float cy, float r) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.argb(240, 105, 235, 190));
            canvas.drawCircle(cx, cy, r, paint);
        }

        private void drawSelectionRect(Canvas canvas, RectF r) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.argb(240, 105, 235, 190));
            canvas.drawRoundRect(r, dp(18), dp(18), paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int index = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                pointerDown(event, index);
            } else if (action == MotionEvent.ACTION_MOVE) {
                pointerMove(event);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                pointerUp(event, index);
            }
            invalidate();
            return true;
        }

        private void pointerDown(MotionEvent event, int index) {
            float x = event.getX(index);
            float y = event.getY(index);
            for (UiButton b : uiButtons) {
                if (b.rect.contains(x, y)) {
                    runAction(b.action);
                    return;
                }
            }

            int hit = findControlIndex(x, y);
            if (editMode) {
                selectedIndex = hit;
                if (hit >= 0) {
                    editPointerId = event.getPointerId(index);
                    Control c = profile().controls.get(hit);
                    dragOffsetX = c.x * getWidth() - x;
                    dragOffsetY = c.y * getHeight() - y;
                }
                return;
            }

            if (hit >= 0) {
                Control c = profile().controls.get(hit);
                c.pointerId = event.getPointerId(index);
                if (TYPE_BUTTON.equals(c.type)) c.pressed = true;
                updateInput(c, x, y);
            }
        }

        private void pointerMove(MotionEvent event) {
            if (editMode) {
                if (selectedIndex < 0 || editPointerId == NO_POINTER) return;
                int pointerIndex = event.findPointerIndex(editPointerId);
                if (pointerIndex < 0) return;
                Control c = selectedControl();
                if (c == null) return;
                float nx = (event.getX(pointerIndex) + dragOffsetX) / Math.max(1f, getWidth());
                float ny = (event.getY(pointerIndex) + dragOffsetY) / Math.max(1f, getHeight());
                if (snapEnabled) {
                    nx = snapUnit(nx);
                    ny = snapUnit(ny);
                }
                float minY = Math.max(0.12f, toolbarBottom / Math.max(1f, getHeight()) + 0.04f);
                c.x = clamp(nx, 0.04f, 0.96f);
                c.y = clamp(ny, minY, 0.9f);
                saveControllers();
                return;
            }

            for (int i = 0; i < event.getPointerCount(); i++) {
                int pointerId = event.getPointerId(i);
                for (Control c : profile().controls) {
                    if (c.pointerId == pointerId) updateInput(c, event.getX(i), event.getY(i));
                }
            }
        }

        private void pointerUp(MotionEvent event, int index) {
            int pointerId = event.getPointerId(index);
            if (editMode) {
                if (pointerId == editPointerId) editPointerId = NO_POINTER;
                return;
            }
            for (Control c : profile().controls) {
                if (c.pointerId == pointerId) {
                    if (TYPE_BUTTON.equals(c.type)) c.pressed = false;
                    if (TYPE_JOYSTICK.equals(c.type)) {
                        c.joyX = 0f;
                        c.joyY = 0f;
                    }
                    c.pointerId = NO_POINTER;
                }
            }
        }

        private int findControlIndex(float px, float py) {
            List<Control> list = profile().controls;
            int w = getWidth();
            int h = getHeight();
            for (int i = list.size() - 1; i >= 0; i--) {
                Control c = list.get(i);
                float cx = c.x * w;
                float cy = c.y * h;
                if (TYPE_SLIDER.equals(c.type)) {
                    float rw = Math.max(dp(44), c.w * w * 2f);
                    float rh = c.h * h;
                    if (px >= cx - rw / 2f && px <= cx + rw / 2f && py >= cy - rh / 2f && py <= cy + rh / 2f) return i;
                } else {
                    float r = c.w * Math.min(w, h) * 1.35f;
                    float dx = px - cx;
                    float dy = py - cy;
                    if (dx * dx + dy * dy <= r * r) return i;
                }
            }
            return -1;
        }

        private void updateInput(Control c, float px, float py) {
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            if (TYPE_JOYSTICK.equals(c.type)) {
                float cx = c.x * w;
                float cy = c.y * h;
                float radius = Math.max(1f, c.w * Math.min(w, h) * 0.58f);
                c.joyX = clamp((px - cx) / radius, -1f, 1f);
                c.joyY = clamp((py - cy) / radius, -1f, 1f);
                float len = (float) Math.sqrt(c.joyX * c.joyX + c.joyY * c.joyY);
                if (len > 1f) {
                    c.joyX /= len;
                    c.joyY /= len;
                }
            } else if (TYPE_SLIDER.equals(c.type)) {
                float top = c.y * h - c.h * h / 2f;
                float bottom = c.y * h + c.h * h / 2f;
                c.value = clamp(1f - (py - top) / Math.max(1f, bottom - top), 0f, 1f);
            }
        }

        private void runAction(String action) {
            if ("edit".equals(action)) {
                editMode = true;
            } else if ("play".equals(action)) {
                editMode = false;
                selectedIndex = -1;
                releaseAllInputs();
                saveControllers();
            } else if ("next_controller".equals(action)) {
                currentController = (currentController + 1) % controllers.size();
                selectedIndex = -1;
                releaseAllInputs();
                saveControllers();
            } else if ("new_controller".equals(action)) {
                promptText("New controller name", "Controller " + (controllers.size() + 1), text -> {
                    ControllerProfile p = new ControllerProfile(clean(text, "Controller " + (controllers.size() + 1)));
                    controllers.add(p);
                    currentController = controllers.size() - 1;
                    selectedIndex = -1;
                    editMode = true;
                    saveControllers();
                    invalidate();
                });
            } else if ("rename_controller".equals(action)) {
                promptText("Rename controller", profile().name, text -> {
                    profile().name = clean(text, profile().name);
                    saveControllers();
                    invalidate();
                });
            } else if ("toggle_snap".equals(action)) {
                snapEnabled = !snapEnabled;
                Toast.makeText(context, snapEnabled ? "Snapping on" : "Snapping off", Toast.LENGTH_SHORT).show();
            } else if ("add_button".equals(action)) {
                addControl(TYPE_BUTTON);
            } else if ("add_joystick".equals(action)) {
                addControl(TYPE_JOYSTICK);
            } else if ("add_slider".equals(action)) {
                addControl(TYPE_SLIDER);
            } else if ("rename_control".equals(action)) {
                Control c = selectedControl();
                if (c == null) return toastSelect();
                promptText("Control label", c.label, text -> {
                    c.label = clean(text, c.label);
                    saveControllers();
                    invalidate();
                });
            } else if ("delete_control".equals(action)) {
                deleteSelected();
            } else if ("size_down".equals(action)) {
                resizeSelected(-1);
            } else if ("size_up".equals(action)) {
                resizeSelected(1);
            } else if ("limits".equals(action)) {
                promptLimits();
            }
        }

        private void addControl(String type) {
            Control c;
            if (TYPE_JOYSTICK.equals(type)) {
                c = new Control(type, nextLabel("JOY"), 0.5f, 0.55f, 0.12f, 0.12f);
                c.min = -1f;
                c.max = 1f;
            } else if (TYPE_SLIDER.equals(type)) {
                c = new Control(type, nextLabel("SLD"), 0.5f, 0.55f, 0.035f, 0.42f);
                c.min = 0f;
                c.max = 100f;
                c.value = 0.5f;
            } else {
                c = new Control(type, nextButtonLabel(), 0.5f, 0.55f, 0.07f, 0.07f);
                c.min = 0f;
                c.max = 1f;
            }
            if (snapEnabled) {
                c.x = snapUnit(c.x);
                c.y = snapUnit(c.y);
            }
            profile().controls.add(c);
            selectedIndex = profile().controls.size() - 1;
            editMode = true;
            saveControllers();
        }

        private void deleteSelected() {
            if (selectedControl() == null) {
                toastSelect();
                return;
            }
            profile().controls.remove(selectedIndex);
            selectedIndex = -1;
            saveControllers();
        }

        private void resizeSelected(int direction) {
            Control c = selectedControl();
            if (c == null) {
                toastSelect();
                return;
            }
            if (TYPE_SLIDER.equals(c.type)) {
                c.h = clamp(c.h + direction * 0.045f, 0.15f, 0.76f);
                c.w = clamp(c.w + direction * 0.004f, 0.02f, 0.08f);
            } else {
                c.w = clamp(c.w + direction * 0.012f, 0.035f, 0.24f);
                c.h = c.w;
            }
            saveControllers();
        }

        private void promptLimits() {
            Control c = selectedControl();
            if (c == null) {
                toastSelect();
                return;
            }
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) dp(16);
            layout.setPadding(pad, pad / 2, pad, 0);
            EditText minInput = new EditText(context);
            minInput.setHint("Minimum value");
            minInput.setSingleLine(true);
            minInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            minInput.setText(format(c.min));
            EditText maxInput = new EditText(context);
            maxInput.setHint("Maximum value");
            maxInput.setSingleLine(true);
            maxInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            maxInput.setText(format(c.max));
            layout.addView(minInput);
            layout.addView(maxInput);
            new AlertDialog.Builder(context)
                    .setTitle("Set limits for " + c.label)
                    .setView(layout)
                    .setPositiveButton("Save", (dialog, which) -> {
                        try {
                            float min = Float.parseFloat(minInput.getText().toString());
                            float max = Float.parseFloat(maxInput.getText().toString());
                            if (min == max) {
                                Toast.makeText(context, "Min and max cannot be the same", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            c.min = min;
                            c.max = max;
                            saveControllers();
                            invalidate();
                        } catch (Exception ex) {
                            Toast.makeText(context, "Invalid number", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void promptText(String title, String current, TextCallback callback) {
            EditText input = new EditText(context);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setText(current);
            input.setSelectAllOnFocus(true);
            int pad = (int) dp(18);
            input.setPadding(pad, pad / 2, pad, pad / 2);
            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setView(input)
                    .setPositiveButton("Save", (dialog, which) -> callback.done(input.getText().toString()))
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private Control selectedControl() {
            if (selectedIndex < 0 || selectedIndex >= profile().controls.size()) return null;
            return profile().controls.get(selectedIndex);
        }

        private void toastSelect() {
            Toast.makeText(context, "Tap a control first", Toast.LENGTH_SHORT).show();
        }

        private void releaseAllInputs() {
            for (Control c : profile().controls) {
                c.pointerId = NO_POINTER;
                c.pressed = false;
                c.joyX = 0f;
                c.joyY = 0f;
            }
        }

        private String nextLabel(String prefix) {
            int count = 1;
            for (Control c : profile().controls) {
                if (c.label.startsWith(prefix)) count++;
            }
            return prefix + count;
        }

        private String nextButtonLabel() {
            String[] names = {"A", "B", "X", "Y", "LB", "RB", "M1", "M2", "M3", "M4"};
            int count = 0;
            for (Control c : profile().controls) if (TYPE_BUTTON.equals(c.type)) count++;
            return names[count % names.length];
        }

        private void loadControllers() {
            String json = prefs.getString(KEY_PROFILES, null);
            if (json == null) json = prefs.getString(OLD_KEY_PROFILES, null);
            if (json == null) {
                createDefaults();
                saveControllers();
                return;
            }
            try {
                JSONArray arr = new JSONArray(json);
                controllers.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    ControllerProfile p = new ControllerProfile(item.optString("name", "Controller " + (i + 1)));
                    JSONArray controls = item.optJSONArray("controls");
                    if (controls != null) {
                        for (int j = 0; j < controls.length(); j++) {
                            JSONObject o = controls.getJSONObject(j);
                            String type = o.optString("type", o.optString("kind", TYPE_BUTTON));
                            Control c = new Control(
                                    type,
                                    o.optString("label", TYPE_BUTTON.equals(type) ? "A" : type),
                                    (float) o.optDouble("x", 0.5),
                                    (float) o.optDouble("y", 0.5),
                                    (float) o.optDouble("w", 0.08),
                                    (float) o.optDouble("h", 0.08)
                            );
                            c.value = (float) o.optDouble("value", 0.5);
                            c.min = (float) o.optDouble("min", TYPE_JOYSTICK.equals(type) ? -1 : 0);
                            c.max = (float) o.optDouble("max", TYPE_SLIDER.equals(type) ? 100 : 1);
                            p.controls.add(c);
                        }
                    }
                    controllers.add(p);
                }
                if (controllers.isEmpty()) createDefaults();
            } catch (Exception ex) {
                controllers.clear();
                createDefaults();
            }
        }

        private void saveControllers() {
            try {
                JSONArray arr = new JSONArray();
                for (ControllerProfile p : controllers) {
                    JSONObject item = new JSONObject();
                    item.put("name", p.name);
                    JSONArray controls = new JSONArray();
                    for (Control c : p.controls) {
                        JSONObject o = new JSONObject();
                        o.put("type", c.type);
                        o.put("label", c.label);
                        o.put("x", c.x);
                        o.put("y", c.y);
                        o.put("w", c.w);
                        o.put("h", c.h);
                        o.put("value", c.value);
                        o.put("min", c.min);
                        o.put("max", c.max);
                        controls.put(o);
                    }
                    item.put("controls", controls);
                    arr.put(item);
                }
                prefs.edit().putString(KEY_PROFILES, arr.toString()).apply();
            } catch (Exception ignored) {
            }
        }

        private void createDefaults() {
            controllers.clear();
            ControllerProfile gamepad = new ControllerProfile("Gamepad");
            gamepad.controls.add(new Control(TYPE_JOYSTICK, "MOVE", 0.18f, 0.62f, 0.145f, 0.145f));
            gamepad.controls.add(new Control(TYPE_JOYSTICK, "LOOK", 0.82f, 0.62f, 0.145f, 0.145f));
            gamepad.controls.add(new Control(TYPE_BUTTON, "A", 0.67f, 0.43f, 0.065f, 0.065f));
            gamepad.controls.add(new Control(TYPE_BUTTON, "B", 0.75f, 0.33f, 0.065f, 0.065f));
            gamepad.controls.add(new Control(TYPE_BUTTON, "X", 0.75f, 0.54f, 0.065f, 0.065f));
            gamepad.controls.add(new Control(TYPE_BUTTON, "Y", 0.83f, 0.43f, 0.065f, 0.065f));
            Control throttle = new Control(TYPE_SLIDER, "THROTTLE", 0.50f, 0.58f, 0.032f, 0.42f);
            throttle.min = 0f;
            throttle.max = 100f;
            throttle.value = 0.55f;
            gamepad.controls.add(throttle);
            controllers.add(gamepad);

            ControllerProfile blank = new ControllerProfile("Blank custom");
            controllers.add(blank);
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        private float sp(float value) {
            return value * getResources().getDisplayMetrics().scaledDensity;
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private float gridPx() {
            return Math.max(dp(28), Math.min(getWidth(), getHeight()) * 0.06f);
        }

        private float snapUnit(float value) {
            float pixels = value * (float) Math.max(1, getWidth());
            float snapped = Math.round(pixels / gridPx()) * gridPx();
            return snapped / Math.max(1f, getWidth());
        }

        private String clean(String text, String fallback) {
            if (text == null) return fallback;
            String t = text.trim();
            if (t.length() == 0) return fallback;
            if (t.length() > 16) t = t.substring(0, 16);
            return t;
        }

        private String format(float v) {
            if (Math.abs(v - Math.round(v)) < 0.05f) return String.valueOf(Math.round(v));
            return String.format(Locale.US, "%.1f", v);
        }

        private interface TextCallback {
            void done(String text);
        }

        private static class UiButton {
            final RectF rect;
            final String action;

            UiButton(RectF rect, String action) {
                this.rect = rect;
                this.action = action;
            }
        }

        private static class ControllerProfile {
            String name;
            final List<Control> controls = new ArrayList<>();

            ControllerProfile(String name) {
                this.name = name;
            }
        }

        private static class Control {
            final String type;
            String label;
            float x;
            float y;
            float w;
            float h;
            float value = 0.5f;
            float min = 0f;
            float max = 1f;
            float joyX = 0f;
            float joyY = 0f;
            boolean pressed = false;
            int pointerId = NO_POINTER;

            Control(String type, String label, float x, float y, float w, float h) {
                this.type = type;
                this.label = label;
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
                if (TYPE_JOYSTICK.equals(type)) {
                    min = -1f;
                    max = 1f;
                } else if (TYPE_SLIDER.equals(type)) {
                    min = 0f;
                    max = 100f;
                }
            }

            float outputValue() {
                return min + value * (max - min);
            }
        }
    }
}
