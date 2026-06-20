package com.phonetroller;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        setContentView(new ControllerView(this));
    }

    public static class ControllerView extends View {
        private static final String PREFS = "phonetroller_profiles";
        private static final String KEY_PROFILES = "profiles_json";
        private static final int NONE = -1;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Profile> profiles = new ArrayList<>();
        private final List<TopButton> topButtons = new ArrayList<>();
        private final RectF temp = new RectF();
        private final SharedPreferences prefs;

        private int currentProfile = 0;
        private boolean editMode = false;
        private int activePointerId = NONE;
        private Control activeControl = null;
        private boolean activeIsEditDrag = false;
        private float dragOffsetX;
        private float dragOffsetY;

        public ControllerView(Context context) {
            super(context);
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            setFocusable(true);
            loadProfiles();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            drawBackground(canvas, w, h);
            drawHeader(canvas, w, h);
            drawControls(canvas, w, h);
            drawFooterHint(canvas, w, h);
        }

        private void drawBackground(Canvas canvas, int w, int h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{Color.rgb(9, 10, 18), Color.rgb(19, 22, 42), Color.rgb(7, 11, 24)},
                    null,
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            paint.setColor(Color.argb(26, 255, 255, 255));
            paint.setStrokeWidth(1f);
            float grid = Math.max(42f, h / 10f);
            for (float x = 0; x < w; x += grid) {
                canvas.drawLine(x, 0, x, h, paint);
            }
            for (float y = 0; y < h; y += grid) {
                canvas.drawLine(0, y, w, y, paint);
            }
        }

        private void drawHeader(Canvas canvas, int w, int h) {
            topButtons.clear();
            float pad = dp(10);
            float barH = dp(56);
            temp.set(pad, pad, w - pad, pad + barH);
            paint.setShader(new LinearGradient(0, temp.top, 0, temp.bottom,
                    Color.argb(210, 34, 38, 70),
                    Color.argb(180, 16, 18, 34),
                    Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(temp, dp(18), dp(18), paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(90, 150, 145, 255));
            canvas.drawRoundRect(temp, dp(18), dp(18), paint);

            textPaint.setTextSize(sp(19));
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Phonetroller", pad + dp(18), pad + dp(35), textPaint);
            textPaint.setFakeBoldText(false);

            float x = pad + dp(175);
            x = addTopButton(canvas, x, pad + dp(11), dp(105), dp(34), editMode ? "Done" : "Edit", "edit");
            x = addTopButton(canvas, x, pad + dp(11), dp(120), dp(34), "Profile " + (currentProfile + 1), "profile");
            x = addTopButton(canvas, x, pad + dp(11), dp(84), dp(34), "+ New", "new_profile");
            x = addTopButton(canvas, x, pad + dp(11), dp(82), dp(34), "+ Joy", "add_joy");
            x = addTopButton(canvas, x, pad + dp(11), dp(92), dp(34), "+ Slider", "add_slider");
            x = addTopButton(canvas, x, pad + dp(11), dp(92), dp(34), "+ Button", "add_button");
            addTopButton(canvas, x, pad + dp(11), dp(76), dp(34), "Clear", "clear");
        }

        private float addTopButton(Canvas canvas, float x, float y, float width, float height, String label, String action) {
            RectF r = new RectF(x, y, x + width, y + height);
            topButtons.add(new TopButton(r, action));
            paint.setStyle(Paint.Style.FILL);
            int leftColor = action.equals("edit") && editMode ? Color.rgb(105, 235, 190) : Color.rgb(96, 88, 230);
            int rightColor = action.equals("clear") ? Color.rgb(255, 91, 126) : Color.rgb(106, 190, 255);
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, leftColor, rightColor, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, dp(14), dp(14), paint);
            paint.setShader(null);
            paint.setColor(Color.argb(70, 255, 255, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            canvas.drawRoundRect(r, dp(14), dp(14), paint);

            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(12));
            textPaint.setFakeBoldText(true);
            canvas.drawText(label, r.centerX(), r.centerY() + dp(4), textPaint);
            textPaint.setFakeBoldText(false);
            return x + width + dp(8);
        }

        private void drawControls(Canvas canvas, int w, int h) {
            if (profiles.isEmpty()) {
                createDefaults();
            }
            Profile profile = profiles.get(currentProfile);
            for (Control c : profile.controls) {
                if ("joystick".equals(c.type)) {
                    drawJoystick(canvas, c, w, h);
                } else if ("slider".equals(c.type)) {
                    drawSlider(canvas, c, w, h);
                } else {
                    drawButton(canvas, c, w, h);
                }
            }
        }

        private void drawJoystick(Canvas canvas, Control c, int w, int h) {
            float cx = c.x * w;
            float cy = c.y * h;
            float radius = c.w * Math.min(w, h);
            float knob = radius * 0.42f;
            float kx = cx + c.inputX * radius * 0.55f;
            float ky = cy + c.inputY * radius * 0.55f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(72, 120, 118, 255));
            canvas.drawCircle(cx, cy, radius * 1.08f, paint);
            paint.setColor(Color.argb(55, 0, 0, 0));
            canvas.drawCircle(cx + dp(4), cy + dp(5), radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.argb(210, 144, 151, 255));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(70, 255, 255, 255));
            canvas.drawLine(cx - radius * 0.72f, cy, cx + radius * 0.72f, cy, paint);
            canvas.drawLine(cx, cy - radius * 0.72f, cx, cy + radius * 0.72f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(kx - knob, ky - knob, kx + knob, ky + knob,
                    Color.rgb(118, 234, 255), Color.rgb(137, 105, 255), Shader.TileMode.CLAMP));
            canvas.drawCircle(kx, ky, knob, paint);
            paint.setShader(null);
            paint.setColor(Color.argb(110, 255, 255, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawCircle(kx, ky, knob, paint);

            drawControlLabel(canvas, c.label, cx, cy + radius + dp(22));
            if (editMode) {
                drawEditOutline(canvas, cx, cy, radius);
            }
        }

        private void drawSlider(Canvas canvas, Control c, int w, int h) {
            float cx = c.x * w;
            float cy = c.y * h;
            float width = c.w * w;
            float height = c.h * h;
            RectF track = new RectF(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(58, 255, 255, 255));
            canvas.drawRoundRect(track, width / 2f, width / 2f, paint);

            float fillTop = track.bottom - track.height() * c.value;
            RectF fill = new RectF(track.left, fillTop, track.right, track.bottom);
            paint.setShader(new LinearGradient(fill.left, fill.top, fill.right, fill.bottom,
                    Color.rgb(96, 243, 210), Color.rgb(124, 110, 255), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(fill, width / 2f, width / 2f, paint);
            paint.setShader(null);

            float knobY = track.bottom - track.height() * c.value;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, knobY, width * 0.78f, paint);
            paint.setColor(Color.argb(130, 110, 100, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawCircle(cx, knobY, width * 0.78f, paint);

            drawControlLabel(canvas, c.label + " " + Math.round(c.value * 100) + "%", cx, track.bottom + dp(24));
            if (editMode) {
                drawEditRect(canvas, track);
            }
        }

        private void drawButton(Canvas canvas, Control c, int w, int h) {
            float cx = c.x * w;
            float cy = c.y * h;
            float radius = c.w * Math.min(w, h);
            float scale = c.pressed ? 0.88f : 1f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(60, 0, 0, 0));
            canvas.drawCircle(cx + dp(4), cy + dp(5), radius * scale, paint);
            paint.setShader(new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                    c.pressed ? Color.rgb(255, 220, 118) : Color.rgb(255, 105, 151),
                    c.pressed ? Color.rgb(108, 245, 210) : Color.rgb(128, 103, 255),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius * scale, paint);
            paint.setShader(null);
            paint.setColor(Color.argb(110, 255, 255, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawCircle(cx, cy, radius * scale, paint);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(18));
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(c.label, cx, cy + dp(7), textPaint);
            textPaint.setFakeBoldText(false);
            if (editMode) {
                drawEditOutline(canvas, cx, cy, radius);
            }
        }

        private void drawControlLabel(Canvas canvas, String label, float cx, float y) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(12));
            textPaint.setFakeBoldText(false);
            textPaint.setColor(Color.argb(210, 235, 238, 255));
            canvas.drawText(label, cx, y, textPaint);
        }

        private void drawEditOutline(Canvas canvas, float cx, float cy, float radius) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(210, 105, 235, 190));
            canvas.drawCircle(cx, cy, radius * 1.18f, paint);
        }

        private void drawEditRect(Canvas canvas, RectF r) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.argb(210, 105, 235, 190));
            canvas.drawRoundRect(r, dp(18), dp(18), paint);
        }

        private void drawFooterHint(Canvas canvas, int w, int h) {
            String hint = editMode
                    ? "EDIT MODE: drag controls around. Add more controls from the top bar. Layout saves automatically."
                    : "PLAY MODE: touch joysticks, sliders, and buttons. This is visual only for now.";
            RectF r = new RectF(dp(16), h - dp(44), w - dp(16), h - dp(12));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(125, 0, 0, 0));
            canvas.drawRoundRect(r, dp(14), dp(14), paint);
            textPaint.setTextSize(sp(12));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.argb(215, 245, 247, 255));
            canvas.drawText(hint, w / 2f, r.centerY() + dp(4), textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int index = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                handlePointerDown(event, index);
            } else if (action == MotionEvent.ACTION_MOVE) {
                handlePointerMove(event);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                handlePointerUp(event, index);
            }
            invalidate();
            return true;
        }

        private void handlePointerDown(MotionEvent event, int index) {
            float x = event.getX(index);
            float y = event.getY(index);
            for (TopButton b : topButtons) {
                if (b.rect.contains(x, y)) {
                    runTopAction(b.action);
                    return;
                }
            }

            Control hit = findControl(x, y);
            if (hit == null) {
                return;
            }
            activePointerId = event.getPointerId(index);
            activeControl = hit;
            activeIsEditDrag = editMode;
            if (activeIsEditDrag) {
                dragOffsetX = hit.x * getWidth() - x;
                dragOffsetY = hit.y * getHeight() - y;
            } else {
                hit.pointerId = activePointerId;
                if ("button".equals(hit.type)) {
                    hit.pressed = true;
                }
                updateControlInput(hit, x, y);
            }
        }

        private void handlePointerMove(MotionEvent event) {
            if (activeControl == null || activePointerId == NONE) {
                return;
            }
            int index = event.findPointerIndex(activePointerId);
            if (index < 0) {
                return;
            }
            float x = event.getX(index);
            float y = event.getY(index);
            if (activeIsEditDrag) {
                activeControl.x = clamp((x + dragOffsetX) / Math.max(1f, getWidth()), 0.06f, 0.94f);
                activeControl.y = clamp((y + dragOffsetY) / Math.max(1f, getHeight()), 0.18f, 0.88f);
                saveProfiles();
            } else {
                updateControlInput(activeControl, x, y);
            }
        }

        private void handlePointerUp(MotionEvent event, int index) {
            int pointerId = event.getPointerId(index);
            if (activeControl != null && pointerId == activePointerId) {
                if (!activeIsEditDrag) {
                    if ("joystick".equals(activeControl.type)) {
                        activeControl.inputX = 0f;
                        activeControl.inputY = 0f;
                    }
                    if ("button".equals(activeControl.type)) {
                        activeControl.pressed = false;
                    }
                    activeControl.pointerId = NONE;
                }
                activeControl = null;
                activePointerId = NONE;
                activeIsEditDrag = false;
            }
        }

        private void updateControlInput(Control c, float px, float py) {
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            if ("joystick".equals(c.type)) {
                float cx = c.x * w;
                float cy = c.y * h;
                float radius = c.w * Math.min(w, h) * 0.55f;
                c.inputX = clamp((px - cx) / radius, -1f, 1f);
                c.inputY = clamp((py - cy) / radius, -1f, 1f);
                float len = (float) Math.sqrt(c.inputX * c.inputX + c.inputY * c.inputY);
                if (len > 1f) {
                    c.inputX /= len;
                    c.inputY /= len;
                }
            } else if ("slider".equals(c.type)) {
                float top = c.y * h - (c.h * h) / 2f;
                float bottom = c.y * h + (c.h * h) / 2f;
                c.value = clamp(1f - (py - top) / Math.max(1f, bottom - top), 0f, 1f);
            }
        }

        private Control findControl(float px, float py) {
            Profile profile = profiles.get(currentProfile);
            for (int i = profile.controls.size() - 1; i >= 0; i--) {
                Control c = profile.controls.get(i);
                int w = getWidth();
                int h = getHeight();
                float cx = c.x * w;
                float cy = c.y * h;
                if ("slider".equals(c.type)) {
                    float rw = c.w * w * 1.6f;
                    float rh = c.h * h;
                    if (px >= cx - rw / 2f && px <= cx + rw / 2f && py >= cy - rh / 2f && py <= cy + rh / 2f) {
                        return c;
                    }
                } else {
                    float r = c.w * Math.min(w, h) * 1.25f;
                    float dx = px - cx;
                    float dy = py - cy;
                    if (dx * dx + dy * dy <= r * r) {
                        return c;
                    }
                }
            }
            return null;
        }

        private void runTopAction(String action) {
            if ("edit".equals(action)) {
                editMode = !editMode;
            } else if ("profile".equals(action)) {
                currentProfile = (currentProfile + 1) % profiles.size();
            } else if ("new_profile".equals(action)) {
                Profile p = new Profile("Profile " + (profiles.size() + 1));
                p.controls.add(new Control("joystick", "L", 0.18f, 0.58f, 0.14f, 0.14f));
                p.controls.add(new Control("button", "A", 0.78f, 0.57f, 0.075f, 0.075f));
                profiles.add(p);
                currentProfile = profiles.size() - 1;
                editMode = true;
                saveProfiles();
            } else if ("add_joy".equals(action)) {
                profiles.get(currentProfile).controls.add(new Control("joystick", nextLabel("J"), 0.50f, 0.58f, 0.12f, 0.12f));
                editMode = true;
                saveProfiles();
            } else if ("add_slider".equals(action)) {
                Control c = new Control("slider", nextLabel("S"), 0.50f, 0.54f, 0.035f, 0.43f);
                c.value = 0.5f;
                profiles.get(currentProfile).controls.add(c);
                editMode = true;
                saveProfiles();
            } else if ("add_button".equals(action)) {
                profiles.get(currentProfile).controls.add(new Control("button", nextButtonLabel(), 0.50f, 0.60f, 0.07f, 0.07f));
                editMode = true;
                saveProfiles();
            } else if ("clear".equals(action)) {
                profiles.get(currentProfile).controls.clear();
                editMode = true;
                saveProfiles();
            }
        }

        private String nextLabel(String prefix) {
            return prefix + (profiles.get(currentProfile).controls.size() + 1);
        }

        private String nextButtonLabel() {
            String[] labels = {"A", "B", "X", "Y", "LB", "RB", "M1", "M2", "M3", "M4"};
            int count = 0;
            for (Control c : profiles.get(currentProfile).controls) {
                if ("button".equals(c.type)) {
                    count++;
                }
            }
            return labels[count % labels.length];
        }

        private void loadProfiles() {
            String json = prefs.getString(KEY_PROFILES, null);
            if (json == null) {
                createDefaults();
                saveProfiles();
                return;
            }
            try {
                JSONArray arr = new JSONArray(json);
                profiles.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    Profile p = new Profile(item.optString("name", "Profile " + (i + 1)));
                    JSONArray controls = item.optJSONArray("controls");
                    if (controls != null) {
                        for (int j = 0; j < controls.length(); j++) {
                            JSONObject o = controls.getJSONObject(j);
                            Control c = new Control(
                                    o.optString("type", "button"),
                                    o.optString("label", "A"),
                                    (float) o.optDouble("x", 0.5),
                                    (float) o.optDouble("y", 0.5),
                                    (float) o.optDouble("w", 0.08),
                                    (float) o.optDouble("h", 0.08)
                            );
                            c.value = (float) o.optDouble("value", 0.5);
                            p.controls.add(c);
                        }
                    }
                    profiles.add(p);
                }
                if (profiles.isEmpty()) {
                    createDefaults();
                }
            } catch (Exception ignored) {
                profiles.clear();
                createDefaults();
            }
        }

        private void saveProfiles() {
            try {
                JSONArray arr = new JSONArray();
                for (Profile p : profiles) {
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
                        controls.put(o);
                    }
                    item.put("controls", controls);
                    arr.put(item);
                }
                prefs.edit().putString(KEY_PROFILES, arr.toString()).apply();
            } catch (Exception ignored) {
                // This is only a placeholder app. If saving fails, the UI still works.
            }
        }

        private void createDefaults() {
            profiles.clear();
            Profile gamepad = new Profile("Gamepad");
            gamepad.controls.add(new Control("joystick", "MOVE", 0.18f, 0.58f, 0.145f, 0.145f));
            gamepad.controls.add(new Control("joystick", "LOOK", 0.82f, 0.58f, 0.145f, 0.145f));
            gamepad.controls.add(new Control("button", "A", 0.66f, 0.44f, 0.065f, 0.065f));
            gamepad.controls.add(new Control("button", "B", 0.74f, 0.34f, 0.065f, 0.065f));
            gamepad.controls.add(new Control("button", "X", 0.74f, 0.55f, 0.065f, 0.065f));
            gamepad.controls.add(new Control("button", "Y", 0.82f, 0.44f, 0.065f, 0.065f));
            Control throttle = new Control("slider", "THROTTLE", 0.50f, 0.55f, 0.032f, 0.42f);
            throttle.value = 0.55f;
            gamepad.controls.add(throttle);
            profiles.add(gamepad);

            Profile drone = new Profile("Drone");
            drone.controls.add(new Control("joystick", "PITCH", 0.22f, 0.58f, 0.14f, 0.14f));
            drone.controls.add(new Control("joystick", "CAM", 0.78f, 0.58f, 0.14f, 0.14f));
            Control lift = new Control("slider", "LIFT", 0.08f, 0.54f, 0.032f, 0.46f);
            lift.value = 0.4f;
            drone.controls.add(lift);
            Control speed = new Control("slider", "SPEED", 0.92f, 0.54f, 0.032f, 0.46f);
            speed.value = 0.65f;
            drone.controls.add(speed);
            drone.controls.add(new Control("button", "REC", 0.50f, 0.36f, 0.06f, 0.06f));
            drone.controls.add(new Control("button", "RTL", 0.50f, 0.62f, 0.06f, 0.06f));
            profiles.add(drone);
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

        private static class TopButton {
            final RectF rect;
            final String action;

            TopButton(RectF rect, String action) {
                this.rect = new RectF(rect);
                this.action = action;
            }
        }

        private static class Profile {
            final String name;
            final List<Control> controls = new ArrayList<>();

            Profile(String name) {
                this.name = name;
            }
        }

        private static class Control {
            final String type;
            final String label;
            float x;
            float y;
            float w;
            float h;
            float value = 0.5f;
            float inputX = 0f;
            float inputY = 0f;
            boolean pressed = false;
            int pointerId = NONE;

            Control(String type, String label, float x, float y, float w, float h) {
                this.type = type;
                this.label = label;
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
            }

            @Override
            public String toString() {
                return String.format(Locale.US, "%s %s %.2f %.2f", type, label, x, y);
            }
        }
    }
}
