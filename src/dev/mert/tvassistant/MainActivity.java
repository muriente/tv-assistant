package dev.mert.tvassistant;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity
    implements Tools.Host, AssistantEngine.Listener, MicrophoneProbe.Listener {
  // Set by NavigationService when the remote's Alexa button launches the assistant.
  static volatile boolean voiceButtonRequest;

  // Palette: one dark surface family so dialogs, panels and cards read as a single product.
  private static final int BG = 0xFF0A111F;
  private static final int PANEL = 0xFF121D2E;
  private static final int RAISED = 0xFF17253A;
  private static final int HILITE = 0xFF1E3A50;
  private static final int STROKE = 0xFF2A3C52;
  private static final int ACCENT = 0xFF7DD3FC;
  private static final int TEXT = 0xFFFFFFFF;
  private static final int MUTED = 0xFFA8BCD2;
  private static final int DIM = 0xFF7E93AB;
  private static final int GOOD = 0xFF6EE7A8;
  private static final int WARN = 0xFFF5C46B;
  private static final int BAD = 0xFFFF8A80;

  // Type scale sized for couch distance on a 1080p panel, not a phone.
  private static final int T_TITLE = 26;
  private static final int T_NAV = 20;
  private static final int T_BODY = 23;
  private static final int T_STEP = 18;
  private static final int T_NOTE = 16;
  private static final int T_META = 15;
  private static final int T_STATUS = 17;

  private static final String[] EXAMPLES = {
    "Search The Wire in Stremio",
    "Search web for today's science news",
    "Show device info",
    "Weather in Istanbul"
  };
  private static final String[] EXAMPLE_TITLES = {
    "Find a show", "Browse the web", "Device info", "Weather"
  };
  private static final String[] EXAMPLE_HINTS = {
    "Search a title in Stremio", "Look something up on the web", "Read this TV's details",
    "Current conditions and forecast"
  };

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<Step> steps = new ArrayList<>();
  private final List<TextView> railItems = new ArrayList<>();
  private EditText command;
  private TextView accountStatus, accountMeta, versionLabel;
  private TextView statusText, aiChip, toolChip, runButton, stopButton, micBadge;
  private LinearLayout meter, recentRow, stream;
  private ScrollView streamScroll;
  private TextView assistantCard;
  private Tools tools;
  private ChatAuth auth;
  private AssistantEngine engine;
  private SharedPreferences prefs;
  private TextToSpeech tts;
  private GeminiKey gemini;
  private boolean ttsReady, initiallyOpened, pendingKeyboard, pendingMicTest, showingExamples;
  private MicrophoneProbe micProbe;
  private String lastCommand = "";
  private JSONArray history = new JSONArray();
  private Dialog openPanel;
  private final List<View> meterSegments = new ArrayList<>();

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("assistant", 0);
    try {
      history = new JSONArray(prefs.getString("history", "[]"));
    } catch (Exception ignored) {
    }
    try {
      auth = new ChatAuth(this);
    } catch (Exception e) {
      auth = null;
    }
    tools = new Tools(this, this);
    gemini = new GeminiKey(this);
    engine = new AssistantEngine(this, tools, auth, gemini, this);
    tts =
        new TextToSpeech(
            this,
            status -> {
              ttsReady = status == TextToSpeech.SUCCESS;
            });

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(0);
    root.setBackgroundColor(BG);
    setContentView(root);
    root.addView(rail(), new LinearLayout.LayoutParams(dp(300), -1));
    LinearLayout main = new LinearLayout(this);
    main.setOrientation(1);
    main.setPadding(dp(20), dp(14), dp(20), dp(12));
    root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
    main.addView(commandBar(), new LinearLayout.LayoutParams(-1, dp(56)));
    main.addView(statusBar(), new LinearLayout.LayoutParams(-1, dp(48)));
    LinearLayout.LayoutParams recentParams = new LinearLayout.LayoutParams(-1, dp(50));
    recentParams.topMargin = dp(2);
    main.addView(recentRow(), recentParams);
    LinearLayout.LayoutParams streamParams = new LinearLayout.LayoutParams(-1, 0, 1);
    streamParams.topMargin = dp(6);
    main.addView(streamScroll(), streamParams);

    command.setOnEditorActionListener(
        (v, action, event) -> {
          boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
          if (action == EditorInfo.IME_ACTION_SEARCH
              || action == EditorInfo.IME_ACTION_NEXT
              || action == EditorInfo.IME_ACTION_DONE
              || action == EditorInfo.IME_ACTION_GO
              || enter) {
            if (!enter || event.getAction() == KeyEvent.ACTION_UP) submit(false);
            return true;
          }
          return false;
        });
    command.requestFocus();
    updateAccount();
    refreshRecent();
    showExamples();
    setBusy(false);
  }

  // ---------------------------------------------------------------- navigation rail

  private View rail() {
    // The rail lives in a ScrollView: when the keyboard opens, adjustResize shortens the
    // window, and a fixed-height rail would clip its lower items out of the view hierarchy
    // where they are neither visible nor reachable by the remote.
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(1);
    content.setPadding(dp(12), dp(14), dp(12), dp(12));

    LinearLayout card = new LinearLayout(this);
    card.setOrientation(1);
    card.setPadding(dp(14), dp(10), dp(14), dp(10));
    card.setBackground(panel(RAISED, STROKE));
    TextView title = text("TV Assistant", T_NAV, TEXT);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    card.addView(title);
    accountStatus = text("", T_NAV - 4, MUTED);
    accountStatus.setPadding(0, dp(4), 0, 0);
    card.addView(accountStatus);
    accountMeta = text("", T_META - 2, DIM);
    accountMeta.setPadding(0, dp(2), 0, 0);
    card.addView(accountMeta);
    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
    cardParams.bottomMargin = dp(10);
    content.addView(card, cardParams);

    navButton(content, "Chat", () -> command.requestFocus());
    navButton(content, "History", this::showHistory);
    navButton(content, "Tools", this::toolList);
    navButton(content, "Apps", this::appList);
    navButton(content, "Settings", this::settings);

    versionLabel = text("", T_META - 2, DIM);
    LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-1, -2);
    versionParams.topMargin = dp(6);
    content.addView(versionLabel, versionParams);
    try {
      versionLabel.setText("Version " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
    } catch (Exception ignored) {
    }

    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(PANEL);
    scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
    return scroll;
  }

  private void navButton(LinearLayout parent, String label, Runnable action) {
    TextView item = text(label, T_NAV, MUTED);
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setPadding(dp(16), 0, dp(10), 0);
    item.setMinimumHeight(dp(48));
    item.setId(View.generateViewId());
    GradientDrawable rest = panel(Color.TRANSPARENT, Color.TRANSPARENT);
    GradientDrawable lit = panel(HILITE, ACCENT);
    interactive(item, rest, lit);
    TextColorSwitch paint = new TextColorSwitch(item, MUTED, TEXT);
    item.setOnFocusChangeListener(paint);
    item.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
    p.bottomMargin = dp(3);
    parent.addView(item, p);
    railItems.add(item);
  }

  private final class TextColorSwitch implements View.OnFocusChangeListener {
    private final TextView view;
    private final int normal, focused;

    TextColorSwitch(TextView view, int normal, int focused) {
      this.view = view;
      this.normal = normal;
      this.focused = focused;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
      view.setTextColor(hasFocus ? focused : normal);
      view.setBackground(hasFocus ? panel(HILITE, ACCENT) : panel(Color.TRANSPARENT, Color.TRANSPARENT));
    }
  }

  // ---------------------------------------------------------------- command bar

  private View commandBar() {
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(0);

    micBadge = text("Speak", T_NAV, MUTED);
    micBadge.setGravity(Gravity.CENTER);
    micBadge.setMinHeight(dp(56));
    interactive(micBadge, panel(RAISED, STROKE), panel(HILITE, ACCENT));
    micBadge.setOnClickListener(v -> keyboard());
    LinearLayout.LayoutParams badge = new LinearLayout.LayoutParams(dp(150), dp(56));
    badge.rightMargin = dp(10);
    bar.addView(micBadge, badge);

    command = new EditText(this);
    command.setId(View.generateViewId());
    command.setSingleLine(true);
    command.setInputType(InputType.TYPE_CLASS_TEXT);
    command.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    command.setTextColor(TEXT);
    command.setHintTextColor(DIM);
    command.setTextSize(T_BODY);
    command.setHint("Say or type a command");
    command.setPadding(dp(16), 0, dp(16), 0);
    command.setBackground(panel(RAISED, ACCENT));
    command.setOnFocusChangeListener(
        (v, focused) -> command.setBackground(panel(focused ? HILITE : RAISED, ACCENT)));
    LinearLayout.LayoutParams field = new LinearLayout.LayoutParams(0, dp(56), 1);
    field.rightMargin = dp(10);
    bar.addView(command, field);

    runButton = pill("Run", ACCENT, TEXT, () -> submit(false));
    bar.addView(runButton, new LinearLayout.LayoutParams(dp(150), dp(56)));
    stopButton = pill("Stop", BAD, TEXT, () -> stopAll());
    stopButton.setVisibility(View.GONE);
    LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(dp(150), dp(56));
    stopParams.leftMargin = dp(10);
    bar.addView(stopButton, stopParams);
    return bar;
  }

  private View statusBar() {
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(0);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(dp(4), dp(8), dp(4), dp(4));
    statusText = text("Ready", T_STATUS, DIM);
    bar.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1));
    meter = new LinearLayout(this);
    meter.setOrientation(0);
    meter.setGravity(Gravity.CENTER_VERTICAL);
    meter.setVisibility(View.GONE);
    for (int i = 0; i < 10; i++) {
      View segment = new View(this);
      segment.setBackground(panelDot(STROKE));
      LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(6), dp(16));
      sp.rightMargin = dp(3);
      meter.addView(segment, sp);
      meterSegments.add(segment);
    }
    LinearLayout.LayoutParams meterParams = new LinearLayout.LayoutParams(-2, -2);
    meterParams.rightMargin = dp(14);
    bar.addView(meter, meterParams);
    aiChip = chip("AI 0/0");
    bar.addView(aiChip, new LinearLayout.LayoutParams(-2, dp(30)));
    LinearLayout.LayoutParams toolParams = new LinearLayout.LayoutParams(-2, dp(30));
    toolParams.leftMargin = dp(8);
    toolChip = chip("Steps 0/0");
    bar.addView(toolChip, toolParams);
    return bar;
  }

  private TextView chip(String label) {
    TextView view = text(label, T_META, DIM);
    view.setGravity(Gravity.CENTER);
    view.setPadding(dp(12), 0, dp(12), 0);
    view.setBackground(panel(PANEL, STROKE));
    return view;
  }

  private TextView pill(String label, int accent, int labelColor, Runnable action) {
    TextView view = text(label, T_NAV, labelColor);
    view.setGravity(Gravity.CENTER);
    view.setMinHeight(dp(56));
    view.setId(View.generateViewId());
    interactive(view, panel(accent == ACCENT ? HILITE : RAISED, STROKE), panel(accent, ACCENT));
    view.setOnClickListener(v -> action.run());
    return view;
  }

  // ---------------------------------------------------------------- recent chips

  private View recentRow() {
    recentRow = new LinearLayout(this);
    recentRow.setOrientation(0);
    recentRow.setGravity(Gravity.CENTER_VERTICAL);
    return recentRow;
  }

  private void refreshRecent() {
    recentRow.removeAllViews();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    List<String> recent = new ArrayList<>();
    for (int i = history.length() - 1; i >= 0 && recent.size() < 5; i--) {
      String value = history.optJSONObject(i).optString("command").trim();
      if (value.isEmpty() || !seen.add(value)) continue;
      recent.add(value);
    }
    recentRow.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
    for (String value : recent) {
      TextView chip = text(Json.clip(value, 34), T_NOTE, MUTED);
      chip.setGravity(Gravity.CENTER_VERTICAL);
      chip.setSingleLine(true);
      chip.setPadding(dp(14), 0, dp(14), 0);
      interactive(chip, panel(PANEL, STROKE), panel(HILITE, ACCENT));
      chip.setOnClickListener(v -> useCommand(value));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40));
      p.rightMargin = dp(8);
      recentRow.addView(chip, p);
    }
  }

  private void useCommand(String value) {
    command.setText(value);
    command.setSelection(value.length());
    command.requestFocus();
    status("Press Run, or the remote mic, to continue");
  }

  // ---------------------------------------------------------------- conversation stream

  private View streamScroll() {
    streamScroll = new ScrollView(this);
    streamScroll.setFillViewport(true);
    stream = new LinearLayout(this);
    stream.setOrientation(1);
    streamScroll.addView(stream, new ScrollView.LayoutParams(-1, -2));
    return streamScroll;
  }

  private void scrollToEnd() {
    streamScroll.post(() -> streamScroll.fullScroll(View.FOCUS_DOWN));
  }

  private void showExamples() {
    stream.removeAllViews();
    assistantCard = null;
    steps.clear();
    showingExamples = true;
    TextView heading = text("What would you like to do?", T_TITLE, TEXT);
    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
    headingParams.bottomMargin = dp(14);
    stream.addView(heading, headingParams);
    for (int row = 0; row < 2; row++) {
      LinearLayout pair = new LinearLayout(this);
      pair.setOrientation(0);
      for (int col = 0; col < 2; col++) {
        int index = row * 2 + col;
        if (index >= EXAMPLES.length) break;
        pair.addView(
            exampleCard(EXAMPLE_TITLES[index], EXAMPLE_HINTS[index], EXAMPLES[index]),
            new LinearLayout.LayoutParams(0, dp(92), 1));
      }
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(92));
      if (row == 0) p.bottomMargin = dp(12);
      stream.addView(pair, p);
    }
  }

  private View exampleCard(String title, String hint, String value) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(1);
    card.setPadding(dp(18), dp(14), dp(18), dp(14));
    interactive(card, panel(RAISED, STROKE), panel(HILITE, ACCENT));
    card.setOnClickListener(v -> useCommand(value));
    TextView heading = text(title, T_NAV, TEXT);
    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    card.addView(heading);
    TextView sub = text(hint, T_NOTE, DIM);
    sub.setPadding(0, dp(4), 0, 0);
    card.addView(sub);
    return card;
  }

  private TextView label(String value, int color) {
    TextView view = text(value, T_META, color);
    view.setLetterSpacing(0.14f);
    return view;
  }

  private void requestCard(String value) {
    if (showingExamples) {
      showingExamples = false;
      stream.removeAllViews();
      steps.clear();
    }
    assistantCard = null;
    TextView card = text("", T_STEP + 2, ACCENT);
    card.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    card.setPadding(dp(16), dp(12), dp(16), dp(12));
    card.setBackground(panel(PANEL, STROKE));
    LinearLayout wrap = new LinearLayout(this);
    wrap.setOrientation(1);
    wrap.addView(label("YOU ASKED", ACCENT));
    card.setPadding(0, dp(6), 0, 0);
    card.setText(value);
    wrap.addView(card);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(14);
    stream.addView(wrap, p);
    scrollToEnd();
  }

  private void assistant(String value) {
    if (value == null || value.trim().isEmpty()) return;
    if (assistantCard == null) {
      LinearLayout wrap = new LinearLayout(this);
      wrap.setOrientation(1);
      wrap.addView(label("ASSISTANT", DIM));
      assistantCard = text(value, T_BODY, TEXT);
      assistantCard.setLineSpacing(0f, 1.18f);
      assistantCard.setPadding(0, dp(6), 0, 0);
      wrap.addView(assistantCard);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
      p.bottomMargin = dp(16);
      stream.addView(wrap, p);
    } else {
      assistantCard.setText(value);
    }
    scrollToEnd();
  }

  private void addNote(String value) {
    TextView note = text(value, T_NOTE, DIM);
    note.setPadding(dp(6), dp(3), 0, dp(3));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(6);
    stream.addView(note, p);
    scrollToEnd();
  }

  private void addStep(String name, String detail, int state) {
    Step step = new Step();
    step.name = name;
    step.detail = detail == null ? "" : detail;
    step.state = state;
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(0);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(dp(16), dp(12), dp(16), dp(12));
    interactive(card, panel(PANEL, STROKE), panel(HILITE, ACCENT));
    step.icon = text(icon(state), T_STEP + 2, stateColor(state));
    step.icon.setGravity(Gravity.CENTER);
    card.addView(step.icon, new LinearLayout.LayoutParams(dp(34), -2));
    LinearLayout column = new LinearLayout(this);
    column.setOrientation(1);
    step.title = text(name, T_STEP, TEXT);
    column.addView(step.title);
    step.body = text(collapsed(step.detail), T_NOTE, MUTED);
    step.body.setPadding(0, dp(2), 0, 0);
    column.addView(step.body);
    LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, -2, 1);
    columnParams.leftMargin = dp(10);
    card.addView(column, columnParams);
    step.card = card;
    card.setOnClickListener(v -> toggle(step));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(8);
    stream.addView(card, p);
    steps.add(step);
    while (steps.size() > 60) {
      Step oldest = steps.remove(0);
      if (oldest.card != null && oldest.card.getParent() instanceof LinearLayout)
        ((LinearLayout) oldest.card.getParent()).removeView(oldest.card);
    }
    scrollToEnd();
  }

  private void toggle(Step step) {
    if (step.detail.isEmpty()) return;
    step.expanded = !step.expanded;
    step.body.setText(step.expanded ? step.detail + "\n\nPress OK to collapse" : collapsed(step.detail));
  }

  private void settleStep(String name, String detail, int state) {
    for (int i = steps.size() - 1; i >= 0; i--) {
      Step step = steps.get(i);
      if (step.state != 0 || !step.name.equals(name)) continue;
      step.state = state;
      step.detail = detail;
      step.icon.setText(icon(state));
      step.icon.setTextColor(stateColor(state));
      step.title.setTextColor(state == 2 ? BAD : TEXT);
      step.body.setText(collapsed(detail));
      step.expanded = false;
      scrollToEnd();
      return;
    }
    addStep(name, detail, state);
  }

  private static String icon(int state) {
    if (state == 1) return "✓";
    if (state == 2) return "✗";
    return "…";
  }

  private static int stateColor(int state) {
    if (state == 1) return GOOD;
    if (state == 2) return BAD;
    return ACCENT;
  }

  private static String collapsed(String detail) {
    if (detail == null || detail.isEmpty()) return "";
    return detail.contains("\n") || detail.length() > 110 ? Json.clip(detail, 110) : detail;
  }

  /** Keeps the Steps chip honest for local commands, which never report an AI budget. */
  private void refreshToolChip() {
    if (toolChip == null || tools == null) return;
    int used = tools.usedSteps();
    int limit = Math.max(1, Math.min(16, prefs.getInt("max_tools", 10)));
    toolChip.setText("Steps " + used + "/" + limit);
    toolChip.setTextColor(used >= limit ? BAD : used * 2 >= limit ? WARN : MUTED);
  }

  private void setBudgets(int aiUsed, int aiLimit, int toolsUsed, int toolLimit) {
    aiChip.setText("AI " + aiUsed + "/" + aiLimit);
    toolChip.setText("Steps " + toolsUsed + "/" + toolLimit);
    aiChip.setTextColor(aiUsed >= aiLimit ? BAD : aiUsed * 2 >= aiLimit ? WARN : MUTED);
    toolChip.setTextColor(toolsUsed >= toolLimit ? BAD : toolsUsed * 2 >= toolLimit ? WARN : MUTED);
    if (micProbe != null && micProbe.active()) return;
    meter.setVisibility(View.GONE);
  }

  private void setBusy(boolean busy) {
    stopButton.setVisibility(busy ? View.VISIBLE : View.GONE);
    runButton.setAlpha(busy ? 0.4f : 1f);
    runButton.setEnabled(!busy);
    micBadge.setText(busy ? "Busy" : "Speak");
    micBadge.setTextColor(busy ? DIM : MUTED);
    micBadge.setEnabled(!busy);
    micBadge.setAlpha(busy ? 0.4f : 1f);
    for (TextView item : railItems) {
      item.setEnabled(!busy);
      item.setAlpha(busy ? 0.45f : 1f);
    }
    if (!busy) aiChip.setTextColor(MUTED);
  }

  private void status(String value) {
    if (statusText == null || value == null) return;
    statusText.setText(value);
    statusText.setTextColor(DIM);
  }

  // ---------------------------------------------------------------- dialogs

  /** One tool step in the transcript, updated in place as the engine progresses. */
  private static final class Step {
    String name, detail = "";
    int state;
    boolean expanded;
    LinearLayout card;
    TextView icon, title, body;
  }

  private static final class Row {
    final String label, value;
    final Runnable action;
    final boolean danger;

    Row(String label, String value, Runnable action) {
      this(label, value, action, false);
    }

    Row(String label, String value, Runnable action, boolean danger) {
      this.label = label;
      this.value = value;
      this.action = action;
      this.danger = danger;
    }
  }

  private Dialog sheet(String title, String subtitle, List<Row> rows) {
    return sheet(title, subtitle, null, rows);
  }

  private Dialog sheet(String title, String subtitle, View body, List<Row> rows) {
    Dialog dialog = new Dialog(this);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(1);
    root.setPadding(dp(26), dp(22), dp(26), dp(16));
    root.setBackground(panel(RAISED, STROKE));
    TextView heading = text(title, T_TITLE, TEXT);
    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    root.addView(heading);
    if (subtitle != null && !subtitle.isEmpty()) {
      TextView sub = text(subtitle, T_NOTE, DIM);
      sub.setPadding(0, dp(6), 0, dp(8));
      sub.setLineSpacing(0f, 1.15f);
      root.addView(sub);
    }
    if (body != null) {
      ScrollView bodyScroll = new ScrollView(this);
      bodyScroll.addView(body, new ScrollView.LayoutParams(-1, -2));
      LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, dp(300));
      bodyParams.bottomMargin = dp(10);
      root.addView(bodyScroll, bodyParams);
    }
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(1);
    int focusIndex = -1;
    for (Row row : rows) {
      list.addView(sheetRow(row));
      if (focusIndex < 0 && !row.danger) focusIndex = list.getChildCount() - 1;
    }
    scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
    LinearLayout.LayoutParams scrollParams =
        new LinearLayout.LayoutParams(-1, rows.size() > 6 ? dp(400) : -2);
    root.addView(scroll, scrollParams);
    dialog.setContentView(root);
    dialog.setCanceledOnTouchOutside(false);
    final int firstFocus = focusIndex;
    dialog.setOnShowListener(
        d -> {
          android.view.Window window = dialog.getWindow();
          if (window == null) return;
          window.setBackgroundDrawable(new ColorDrawable(0x00000000));
          window.setDimAmount(0.65f);
          window.setLayout(dp(880), -2);
          if (firstFocus >= 0) {
            View target = list.getChildAt(firstFocus);
            target.setFocusableInTouchMode(true);
            target.requestFocus();
          }
        });
    dialog.setOnDismissListener(
        d -> {
          if (openPanel == dialog) openPanel = null;
        });
    openPanel = dialog;
    dialog.show();
    return dialog;
  }

  /** One settings row: label on the left, current value on the right, whole row focusable. */
  private View sheetRow(Row row) {
    LinearLayout item = new LinearLayout(this);
    item.setOrientation(0);
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setPadding(dp(16), 0, dp(16), 0);
    item.setMinimumHeight(dp(58));
    item.setId(View.generateViewId());
    GradientDrawable rest = panel(PANEL, Color.TRANSPARENT);
    GradientDrawable lit = panel(HILITE, ACCENT);
    item.setBackground(rest);
    item.setFocusable(true);
    item.setFocusableInTouchMode(true);
    int labelColor = row.danger ? BAD : TEXT;
    item.setOnFocusChangeListener((view, hasFocus) -> view.setBackground(hasFocus ? lit : rest));
    TextView name = text(row.label, T_NAV, labelColor);
    item.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    if (row.value != null && !row.value.isEmpty()) {
      TextView value = text(row.value, T_NOTE, DIM);
      value.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
      item.addView(value, new LinearLayout.LayoutParams(-2, -2));
    }
    item.setOnClickListener(v -> row.action.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
    p.bottomMargin = dp(4);
    item.setLayoutParams(p);
    return item;
  }

  /** Closes the open panel, then runs the next action, so panels never stack. */
  private void replace(Runnable next) {
    Dialog current = openPanel;
    openPanel = null;
    if (current != null) {
      current.setOnDismissListener(null);
      current.dismiss();
    }
    handler.post(next);
  }

  private void dismissOpen() {
    Dialog current = openPanel;
    openPanel = null;
    if (current != null) {
      current.setOnDismissListener(null);
      current.dismiss();
    }
  }

  private void ask(String title, String message, Runnable onYes) {
    List<Row> rows = new ArrayList<>();
    rows.add(new Row("Yes, continue", null, () -> replace(onYes)));
    rows.add(new Row("Cancel", null, this::dismissOpen, true));
    sheet(title, message, rows);
  }

  // ---------------------------------------------------------------- settings

  private void settings() {
    List<Row> rows = new ArrayList<>();
    JSONObject active = auth == null ? null : auth.active();
    boolean connected = active != null && ChatAuth.hasPlan(active);
    boolean geminiOn = engine.usesGemini();
    rows.add(
        new Row(
            "Gemini",
            gemini.present() ? (geminiOn ? "Active · " + engine.geminiModel() : "Key stored · not active")
                : "No key",
            this::settingsGemini));
    rows.add(
        new Row(
            "ChatGPT account",
            connected ? (geminiOn ? "Connected · not active" : "Connected") : auth == null ? "Unavailable" : "Local only",
            this::settingsAccount));
    rows.add(new Row("AI limits", "4 requests · 10 steps", this::settingsLimits));
    rows.add(new Row("Permissions", "Navigation and playback", this::settingsPermissions));
    rows.add(new Row("Diagnostics", "Checks and microphone", this::settingsDiagnostics));
    rows.add(new Row("Close", null, this::dismissOpen));
    sheet(
        "Settings",
        geminiOn
            ? "Using Gemini. Commands are interpreted by AI."
            : connected
                ? "Connected to ChatGPT. Commands are interpreted by AI."
                : "Not connected. Basic commands still work offline.",
        rows);
  }

  private void settingsGemini() {
    List<Row> rows = new ArrayList<>();
    rows.add(new Row("Choose Gemini model", engine.geminiModel(), () -> replace(this::geminiModels)));
    rows.add(
        new Row(
            "Prefer ChatGPT when signed in",
            toggle("chatgpt".equals(prefs.getString("provider", "auto"))),
            () ->
                replace(
                    () -> {
                      boolean chatgpt = "chatgpt".equals(prefs.getString("provider", "auto"));
                      prefs.edit().putString("provider", chatgpt ? "auto" : "chatgpt").apply();
                      engine.clear();
                      updateAccount();
                      settingsGemini();
                    })));
    if (gemini.present())
      rows.add(
          new Row(
              "Remove Gemini key",
              null,
              () -> replace(() -> {
                gemini.clear();
                engine.clear();
                updateAccount();
                status("Gemini key removed");
              }),
              true));
    rows.add(new Row("Close", null, this::dismissOpen));
    sheet(
        "Gemini",
        gemini.present()
            ? "A Google AI Studio key is stored encrypted on this TV."
            : "No key yet. Provision it from a computer: install.py --gemini-key-file <file>.",
        rows);
  }

  private void geminiModels() {
    if (!gemini.present()) {
      assistant("Provision a Gemini API key first.");
      return;
    }
    status("Loading Gemini models…");
    new Thread(
            () -> {
              try {
                JSONArray catalog = engine.geminiCatalog();
                String[] labels = new String[catalog.length()];
                for (int i = 0; i < labels.length; i++)
                  labels[i] = catalog.getJSONObject(i).getString("display_name");
                handler.post(
                    () ->
                        sheet(
                            "Choose Gemini model",
                            "Default: " + Gemini.DEFAULT_MODEL + ", fallback " + Gemini.FALLBACK_MODEL + ".",
                            rowsFor(
                                labels,
                                index -> {
                                  prefs.edit().putString("gemini_model", catalog.optJSONObject(index).optString("slug")).apply();
                                  updateAccount();
                                  status("Selected " + labels[index]);
                                })));
              } catch (Exception e) {
                status(ChatAuth.safe(e));
              }
            })
        .start();
  }

  private void settingsAccount() {
    List<Row> rows = new ArrayList<>();
    rows.add(new Row("Continue with ChatGPT", null, () -> replace(() -> login(false))));
    rows.add(new Row("Add ChatGPT account", null, () -> replace(() -> login(true))));
    rows.add(new Row("Choose saved account", null, () -> replace(this::accountPicker)));
    rows.add(new Row("Choose AI model", null, () -> replace(this::models)));
    rows.add(new Row("Manage ChatGPT usage", null, this::openUsage));
    rows.add(new Row("Sign out", null, () -> replace(this::signOut), true));
    rows.add(new Row("Close", null, this::dismissOpen));
    sheet("ChatGPT account", "Sign in on this TV. Only Plus and Pro plans can power AI tasks.", rows);
  }

  private void settingsLimits() {
    List<Row> rows = new ArrayList<>();
    rows.add(
        new Row(
            "AI request limit",
            String.valueOf(prefs.getInt("max_rounds", 4)),
            () -> replace(
                () ->
                    limits(
                        "max_rounds",
                        new int[] {1, 2, 4, 6, 8, 12},
                        "Maximum AI requests per command",
                        this::settingsLimits))));
    rows.add(
        new Row(
            "Tool step limit",
            String.valueOf(prefs.getInt("max_tools", 10)),
            () -> replace(
                () ->
                    limits(
                        "max_tools",
                        new int[] {4, 8, 10, 16},
                        "Maximum tool steps per command",
                        this::settingsLimits))));
    rows.add(
        new Row(
            "Local-only mode",
            toggle(prefs.getBoolean("local_only", false)),
            () ->
                replace(
                    () -> {
                      prefs.edit().putBoolean("local_only", !prefs.getBoolean("local_only", false)).apply();
                      settingsLimits();
                    })));
    rows.add(
        new Row(
            "Spoken answers",
            toggle(prefs.getBoolean("speech", false)),
            () ->
                replace(
                    () -> {
                      prefs.edit().putBoolean("speech", !prefs.getBoolean("speech", false)).apply();
                      settingsLimits();
                    })));
    rows.add(new Row("Close", null, this::dismissOpen));
    sheet("AI limits", "Lower limits finish sooner. The last AI request answers the request.", rows);
  }

  private void settingsPermissions() {
    List<Row> rows = new ArrayList<>();
    rows.add(
        new Row(
            "Screen navigation",
            NavigationService.instance == null ? "Off" : "On",
            this::navigationPermission));
    rows.add(
        new Row(
            "Playback control",
            "Notification access",
            this::playbackPermission));
    rows.add(
        new Row(
            "Screen vision",
            CaptureService.instance == null ? "Off" : "On",
            this::screenVision));
    rows.add(
        new Row(
            "Alexa button opens TV Assistant",
            toggle(prefs.getBoolean(NavigationService.PREF_VOICE_BUTTON, true)),
            () ->
                replace(
                    () -> {
                      prefs
                          .edit()
                          .putBoolean(
                              NavigationService.PREF_VOICE_BUTTON,
                              !prefs.getBoolean(NavigationService.PREF_VOICE_BUTTON, true))
                          .apply();
                      settingsPermissions();
                    })));
    rows.add(new Row("Close", null, this::dismissOpen));
    sheet("Permissions", "These are optional. Enable them from Android settings when asked.", rows);
  }

  private void settingsDiagnostics() {
    List<Row> rows = new ArrayList<>();
    rows.add(new Row("Run diagnostics", null, () -> replace(this::diagnostics)));
    rows.add(new Row("Microphone test", "8 seconds, local only", () -> replace(this::microphoneTest)));
    rows.add(
        new Row(
            "Microphone test source",
            MicrophoneProbe.sourceName(prefs.getInt(MicrophoneProbe.PREF_SOURCE, 0)),
            () ->
                replace(
                    () -> {
                      prefs
                          .edit()
                          .putInt(
                              MicrophoneProbe.PREF_SOURCE,
                              MicrophoneProbe.nextSource(prefs.getInt(MicrophoneProbe.PREF_SOURCE, 0)))
                          .apply();
                      settingsDiagnostics();
                    })));
    rows.add(
        new Row(
            "Bluetooth SCO for microphone test",
            toggle(prefs.getBoolean(MicrophoneProbe.PREF_SCO, false)),
            () ->
                replace(
                    () -> {
                      prefs
                          .edit()
                          .putBoolean(MicrophoneProbe.PREF_SCO, !prefs.getBoolean(MicrophoneProbe.PREF_SCO, false))
                          .apply();
                      settingsDiagnostics();
                    })));
    rows.add(new Row("Clear local history", "Saved commands", () -> replace(this::clearHistory), true));
    rows.add(new Row("Close", null, this::dismissOpen));
    sheet("Diagnostics", "Checks run on this TV. The microphone test never records or uploads audio.", rows);
  }

  private static String toggle(boolean on) {
    return on ? "On" : "Off";
  }

  private void signOut() {
    if (auth == null) return;
    ask("Sign out?", "Removes the saved ChatGPT connection from this TV.", () -> {
      new Thread(
              () -> {
                try {
                  String result = auth.signOut();
                  handler.post(
                      () -> {
                        assistant(result);
                        updateAccount();
                      });
                } catch (Exception e) {
                  status(ChatAuth.safe(e));
                }
              })
          .start();
    });
  }

  private void clearHistory() {
    ask("Clear local history?", "Removes saved commands and answers on this TV.", () -> {
      history = new JSONArray();
      prefs.edit().remove("history").apply();
      engine.clear();
      refreshRecent();
    });
  }

  private void navigationPermission() {
    permission(
        "Screen navigation",
        "This optional service can read visible app labels and click or type when you give a"
            + " command. During AI tasks, relevant screen text goes to your selected model."
            + " Password fields are excluded. Enable TV Assistant navigation in the next screen.",
        "accessibility");
  }

  private void playbackPermission() {
    permission(
        "Playback control",
        "This optional permission gives TV Assistant access to active media sessions through"
            + " Android notification access. Enable TV Assistant playback in the next screen."
            + " Supported players can then be paused, resumed or seeked.",
        "playback");
  }

  private void screenVision() {
    if (CaptureService.instance != null) {
      stopService(new Intent(this, CaptureService.class));
      status("Screen vision stopped");
      return;
    }
    ask(
        "Enable screen vision?",
        "Allows AI to see screenshots of the TV during your tasks, including visible account"
            + " or page details. Images stay in memory and are sent to your chosen model only when"
            + " requested. You can stop this session in Settings. Android will ask for"
            + " screen-capture consent next.",
        () -> {
          try {
            startActivityForResult(
                ((android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE))
                    .createScreenCaptureIntent(),
                902);
          } catch (Exception e) {
            assistant("Screen capture is unavailable on this device: " + ChatAuth.safe(e));
          }
        });
  }

  // ---------------------------------------------------------------- commands

  private void accountPicker() {
    if (auth == null) return;
    JSONArray profiles = auth.profiles();
    String[] labels = new String[profiles.length()];
    for (int i = 0; i < labels.length; i++) {
      JSONObject p = profiles.optJSONObject(i);
      labels[i] =
          (i + 1)
              + " · "
              + p.optString("email")
              + " · "
              + p.optString("client_id").substring(Math.max(0, p.optString("client_id").length() - 7));
    }
    sheet("Saved ChatGPT connections", "Choose which account this TV should use.", rowsFor(labels, index -> {
      try {
        auth.select(profiles.optJSONObject(index).getString("client_id"));
        prefs.edit().remove("model").apply();
        engine.clear();
        updateAccount();
      } catch (Exception e) {
        status(ChatAuth.safe(e));
      }
    }));
  }

  private void models() {
    if (auth == null || !ChatAuth.hasPlan(auth.active())) {
      assistant("Connect ChatGPT and grant plan usage first.");
      return;
    }
    status("Loading eligible models…");
    new Thread(
            () -> {
              try {
                JSONArray catalog = engine.catalog();
                String[] labels = new String[catalog.length()];
                for (int i = 0; i < labels.length; i++)
                  labels[i] = catalog.getJSONObject(i).getString("display_name");
                handler.post(
                    () ->
                        sheet(
                            "Choose AI model",
                            "The newest eligible model is usually the best choice.",
                            rowsFor(
                                labels,
                                index -> {
                                  prefs
                                      .edit()
                                      .putString(
                                          "model", catalog.optJSONObject(index).optString("slug"))
                                      .apply();
                                  status("Selected " + labels[index]);
                                })));
              } catch (Exception e) {
                status(ChatAuth.safe(e));
              }
            })
        .start();
  }

  private void openUsage() {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage")));
    } catch (Exception e) {
      status("No browser is available");
    }
  }

  private void limits(String key, int[] values, String title, Runnable back) {
    String[] labels = new String[values.length];
    for (int i = 0; i < labels.length; i++) labels[i] = String.valueOf(values[i]);
    sheet(title, "Takes effect on the next command.", rowsFor(labels, index -> {
      prefs.edit().putInt(key, values[index]).apply();
      status("Limit set to " + values[index]);
      back.run();
    }));
  }

  private void toolList() {
    JSONArray schemas = tools.schemas();
    String[] labels = new String[schemas.length()];
    for (int i = 0; i < labels.length; i++) labels[i] = schemas.optJSONObject(i).optString("name");
    sheet(
        labels.length + " tools",
        "What the assistant can do. AI tasks are limited to the tools that match your request.",
        rowsFor(
            labels,
            index ->
                sheet(
                    labels[index],
                    schemas.optJSONObject(index).optString("description"),
                    rowsFor(new String[] {"Close"}, i -> dismissOpen()))));
  }

  private void appList() {
    JSONArray apps = tools.apps();
    String[] labels = new String[apps.length()];
    for (int i = 0; i < labels.length; i++) labels[i] = apps.optJSONObject(i).optString("label");
    sheet("Installed apps", "Choose an app to open.", rowsFor(labels, index -> {
      command.setText("Open " + apps.optJSONObject(index).optString("package"));
      dismissOpen();
      submit(false);
    }));
  }

  private void showHistory() {
    List<Row> rows = new ArrayList<>();
    if (history.length() > 0) {
      String[] labels = new String[history.length()];
      for (int i = 0; i < labels.length; i++)
        labels[i] = Json.clip(history.optJSONObject(history.length() - 1 - i).optString("command"), 100);
      for (int i = 0; i < labels.length; i++) {
        final JSONObject item = history.optJSONObject(history.length() - 1 - i);
        rows.add(
            new Row(
                labels[i],
                null,
                () ->
                    replace(
                        () -> {
                          TextView body = text(item.optString("answer"), T_BODY, TEXT);
                          body.setLineSpacing(0f, 1.18f);
                          body.setPadding(0, dp(4), 0, dp(4));
                          List<Row> detail = new ArrayList<>();
                          detail.add(
                              new Row(
                                  "Run again",
                                  null,
                                  () -> {
                                    command.setText(item.optString("command"));
                                    dismissOpen();
                                    submit(false);
                                  }));
                          detail.add(new Row("Close", null, this::dismissOpen));
                          sheet(item.optString("command"), "Saved answer", body, detail);
                        })));
      }
    }
    rows.add(new Row("Clear conversation", "Only this screen", () -> replace(this::clearConversation), true));
    rows.add(new Row("Close", null, this::dismissOpen));
    sheet(
        "Recent commands",
        history.length() == 0 ? "Nothing saved yet." : "Choose a command to see its answer.",
        rows);
  }

  private void clearConversation() {
    showExamples();
    status("Ready");
  }

  private List<Row> rowsFor(String[] labels, Click choice) {
    List<Row> rows = new ArrayList<>();
    for (int i = 0; i < labels.length; i++) {
      final int index = i;
      rows.add(new Row(labels[i], null, () -> choice.pick(index)));
    }
    return rows;
  }

  private interface Click {
    void pick(int index);
  }

  private void diagnostics() {
    if (engine.busy()) {
      status("Finish the current task before diagnostics");
      return;
    }
    assistant("Checking local capabilities…");
    tools.startTask(16);
    new Thread(
            () -> {
              StringBuilder report = new StringBuilder();
              String[] names = {
                "device_info", "list_apps", "clock", "calculate", "volume", "preferences"
              };
              JSONObject[] args = {
                Json.obj(),
                Json.obj(),
                Json.obj(),
                Json.obj("expression", "(12+8)*3/2"),
                Json.obj("action", "get"),
                Json.obj("action", "get")
              };
              int passed = 0;
              for (int i = 0; i < names.length; i++) {
                JSONObject result = tools.execute(names[i], args[i]);
                if (!result.has("error")) passed++;
                report.append(result.has("error") ? "FAIL " : "PASS ").append(names[i]).append("\n");
              }
              report
                  .append("\n")
                  .append(passed)
                  .append("/6 local checks passed.\nNavigation: ")
                  .append(NavigationService.instance != null ? "enabled" : "needs permission")
                  .append("\nGemini: ")
                  .append(gemini.present() ? (engine.usesGemini() ? "active · " + engine.geminiModel() : "key stored, not active") : "no key")
                  .append("\nChatGPT: ")
                  .append(
                      auth != null && ChatAuth.hasPlan(auth.active())
                          ? "connected; inference not tested by diagnostics"
                          : "needs sign-in for AI");
              String text = report.toString();
              handler.post(
                  () -> {
                    assistant(text);
                    status("Diagnostics complete");
                  });
            })
        .start();
  }

  private void permission(String title, String message, String panel) {
    List<Row> rows = new ArrayList<>();
    rows.add(
        new Row(
            "Open settings",
            null,
            () ->
                new Thread(
                        () -> {
                          JSONObject r = tools.execute("open_settings", Json.obj("panel", panel));
                          if (r.has("error")) status(r.optString("error"));
                        })
                    .start()));
    rows.add(new Row("Later", null, this::dismissOpen));
    sheet(title, message, rows);
  }

  private void login(boolean add) {
    if (auth == null) {
      assistant("Secure account storage could not initialize on this device.");
      return;
    }
    if (engine.busy()) {
      status("Finish or stop the current task before changing accounts");
      return;
    }
    List<Row> rows = new ArrayList<>();
    rows.add(
        new Row(
            "Continue with ChatGPT",
            null,
            () -> {
              status("Opening ChatGPT sign-in…");
              auth.signIn(
                  add,
                  new ChatAuth.Listener() {
                    public void ready(String url) {
                      handler.post(
                          () -> {
                            try {
                              Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                              if (getPackageManager().getLaunchIntentForPackage("com.amazon.cloud9") != null)
                                i.setPackage("com.amazon.cloud9");
                              startActivity(i);
                            } catch (Exception e) {
                              auth.cancelLogin();
                              status("No system browser is installed");
                            }
                          });
                    }

                    public void result(String message) {
                      handler.post(
                          () -> {
                            assistant(message);
                            updateAccount();
                            status("Return to TV Assistant after sign-in");
                          });
                    }
                  });
            }));
    rows.add(new Row("Later", null, this::dismissOpen));
    sheet(
        "Connect ChatGPT",
        "Sign in using the browser on this TV. Eligible Plus/Pro plans can power AI tasks. You"
            + " choose whether to grant plan usage. Plus usage shares your five-hour allowance"
            + " across participating apps. Basic commands remain available without sign-in.",
        rows);
  }

  // ---------------------------------------------------------------- window and input

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (focused && command != null) {
      if (pendingMicTest) {
        pendingMicTest = false;
        pendingKeyboard = false;
        initiallyOpened = true;
        startMicrophoneTest();
      } else if (pendingKeyboard || !initiallyOpened) {
        initiallyOpened = true;
        pendingKeyboard = false;
        command.post(() -> keyboard());
      }
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (intent != null && intent.getBooleanExtra(NavigationService.PREF_VOICE_BUTTON, false)) showKeyboard();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (accountStatus != null) updateAccount();
    if (voiceButtonRequest) {
      voiceButtonRequest = false;
      showKeyboard();
    }
  }

  private void showKeyboard() {
    if (command == null) return;
    if (hasWindowFocus()) command.post(() -> keyboard());
    else pendingKeyboard = true;
  }

  private void keyboard() {
    command.requestFocus();
    command.setSelection(command.length());
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(command, 0);
  }

  private void microphoneTest() {
    if (micProbe != null && micProbe.active()) {
      micProbe.cancel();
      status("Microphone test stopped");
      return;
    }
    if (engine.busy()) {
      status("Stop the running task before testing the microphone");
      return;
    }
    ask(
        "Local microphone test",
        "ChatGPT plan-sharing does not include OpenAI transcription. This test checks raw"
            + " microphone access for 8 seconds. Only levels are measured; audio is not saved"
            + " or sent anywhere. Remote microphone access is unverified. Holding Alexa may open"
            + " its system UI and interrupt the test. The test stops when you leave the app."
            + " Bluetooth SCO is optional in Diagnostics and may temporarily change audio routing.",
        this::queueMicrophoneTest);
  }

  private void queueMicrophoneTest() {
    pendingMicTest = true;
    command.post(
        () -> {
          if (pendingMicTest && hasWindowFocus()) {
            pendingMicTest = false;
            startMicrophoneTest();
          }
        });
  }

  private void startMicrophoneTest() {
    if (isFinishing() || !hasWindowFocus() || engine.busy()) return;
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(command.getWindowToken(), 0);
    if (micProbe == null) micProbe = new MicrophoneProbe(this, handler, this);
    micBadge.setText("Mic test");
    micBadge.setTextColor(WARN);
    meter.setVisibility(View.VISIBLE);
    status("Starting local microphone test…");
    micProbe.start(prefs.getInt(MicrophoneProbe.PREF_SOURCE, 0), prefs.getBoolean(MicrophoneProbe.PREF_SCO, false));
  }

  private void clearMeter() {
    for (View segment : meterSegments) segment.setBackground(panelDot(STROKE));
    meter.setVisibility(View.GONE);
    micBadge.setText(engine.busy() ? "Busy" : "Speak");
    micBadge.setTextColor(engine.busy() ? DIM : MUTED);
  }

  private GradientDrawable panelDot(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(dp(2));
    d.setColor(color);
    return d;
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] names, int[] grants) {
    super.onRequestPermissionsResult(request, names, grants);
    if (request != 903) return;
    if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) queueMicrophoneTest();
    else status("Microphone permission was denied; keyboard dictation remains available");
  }

  @Override
  public void microphoneState(String value) {
    handler.post(
        () -> {
          status(value);
          int rms = -1;
          int at = value.indexOf("RMS ");
          if (at >= 0) {
            StringBuilder digits = new StringBuilder();
            for (int i = at + 4; i < value.length() && Character.isDigit(value.charAt(i)); i++)
              digits.append(value.charAt(i));
            try {
              rms = Integer.parseInt(digits.toString());
            } catch (Exception ignored) {
            }
          }
          if (rms < 0) return;
          int lit = Math.max(1, Math.min(meterSegments.size(), rms / 400));
          for (int i = 0; i < meterSegments.size(); i++)
            meterSegments.get(i)
                .setBackground(panelDot(i < lit ? (i > meterSegments.size() - 3 ? WARN : GOOD) : STROKE));
        });
  }

  @Override
  public void microphoneResult(String report) {
    handler.post(
        () -> {
          clearMeter();
          status("Local microphone test complete — no transcription attempted");
          assistant(report);
        });
  }

  @Override
  public void microphoneError(String message) {
    handler.post(
        () -> {
          clearMeter();
          status("Microphone test unavailable");
          assistant(message);
        });
  }

  @Override
  protected void onPause() {
    pendingMicTest = false;
    if (micProbe != null && micProbe.active()) {
      micProbe.cancel();
      handler.post(() -> {
        clearMeter();
        status("Microphone test stopped when the app lost focus");
      });
    }
    super.onPause();
  }

  private void stopAll() {
    pendingMicTest = false;
    engine.stop();
    if (micProbe != null && micProbe.active()) {
      micProbe.cancel();
      clearMeter();
      status("Microphone test stopped");
    }
  }

  private void submit(boolean force) {
    String text = command.getText().toString().trim();
    if (text.isEmpty()) {
      status("Say or type a command first");
      return;
    }
    if (micProbe != null && micProbe.active()) {
      status("Stop the microphone test before running a command");
      return;
    }
    if (engine.busy()) {
      status("A task is running. Use Stop before starting another.");
      return;
    }
    lastCommand = text;
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(command.getWindowToken(), 0);
    requestCard(text);
    status("Working on: " + text);
    setBusy(true);
    engine.run(text, force);
  }

  // ---------------------------------------------------------------- host callbacks

  @Override
  public <T> T ui(Callable<T> task) throws Exception {
    if (Looper.myLooper() == Looper.getMainLooper()) return task.call();
    FutureTask<T> future = new FutureTask<>(task);
    handler.post(future);
    try {
      return future.get(20, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) throw (Exception) cause;
      throw e;
    }
  }

  @Override
  public boolean approve(String description) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    boolean[] result = {false};
    handler.post(
        () -> {
          startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
          handler.postDelayed(
              () -> {
                if (isFinishing()) {
                  latch.countDown();
                  return;
                }
                List<Row> rows = new ArrayList<>();
                rows.add(new Row("Allow", null, () -> {
                  result[0] = true;
                  latch.countDown();
                  dismissOpen();
                }));
                rows.add(new Row("Cancel", null, () -> {
                  result[0] = false;
                  latch.countDown();
                  dismissOpen();
                }, true));
                sheet("Confirm action", description, rows);
              },
              350);
        });
    while (!latch.await(250, TimeUnit.MILLISECONDS)) {
      if (engine.cancelled()) return false;
    }
    return result[0];
  }

  @Override
  public void trace(String value) {
    handler.post(
        () -> {
          if (value == null) return;
          String line = value.trim();
          if (line.startsWith("Task timing")) {
            // Task metrics stay out of the transcript; the footer already reports progress.
            return;
          }
          if (line.startsWith("→ ")) {
            addStep(line.substring(2).trim(), "", 0);
            refreshToolChip();
            return;
          }
          if (line.startsWith("✓ ") || line.startsWith("! ")) {
            boolean ok = line.startsWith("✓ ");
            String rest = line.substring(2).trim();
            int dot = rest.indexOf(" · ");
            settleStep(
                (dot < 0 ? rest : rest.substring(0, dot)).replace('_', ' '),
                dot < 0 ? "" : rest.substring(dot + 3),
                ok ? 1 : 2);
            return;
          }
          addNote(line);
        });
  }

  @Override
  public boolean cancelled() {
    return engine != null && engine.cancelled();
  }

  @Override
  public void state(String value) {
    handler.post(
        () -> {
          if (value == null) return;
          if (value.startsWith("AI · request ")) {
            int[] budget = parseBudget(value);
            if (budget != null) {
              setBudgets(budget[0], budget[1], budget[2], budget[3]);
              return;
            }
          }
          status(value);
        });
  }

  /** Reads the engine's progress string: "AI · request 2/4 · tools 3/10". */
  private static int[] parseBudget(String value) {
    int[] budget = new int[4];
    String[] parts = value.split("·");
    for (String part : parts) {
      String[] sides = part.trim().split("/");
      if (sides.length != 2) continue;
      String label = sides[0].trim();
      int index;
      if (label.endsWith("request")) index = 0;
      else if (label.endsWith("tools")) index = 2;
      else continue;
      try {
        budget[index] = Integer.parseInt(sides[0].replaceAll("[^0-9]", "").trim());
        budget[index + 1] = Integer.parseInt(sides[1].replaceAll("[^0-9]", "").trim());
      } catch (Exception e) {
        return null;
      }
    }
    return budget[0] > 0 && budget[1] > 0 ? budget : null;
  }

  @Override
  public void answer(String value) {
    handler.post(
        () -> {
          assistant(value);
          history.put(
              Json.obj("command", lastCommand, "answer", Json.clip(value, 3000), "at", System.currentTimeMillis()));
          while (history.length() > 20) history.remove(0);
          prefs.edit().putString("history", history.toString()).apply();
          refreshRecent();
          if (prefs.getBoolean("speech", false)) speak(value);
        });
  }

  @Override
  public void finished() {
    handler.post(
        () -> {
          setBusy(false);
          updateAccount();
        });
  }

  @Override
  public void speak(String value) {
    handler.post(
        () -> {
          if (prefs.getBoolean("speech", false) && ttsReady)
            tts.speak(Json.clip(value, 600), TextToSpeech.QUEUE_FLUSH, null, "assistant");
          else trace("Speech output is disabled or no TTS engine is available.");
        });
  }

  private void updateAccount() {
    JSONObject a = auth == null ? null : auth.active();
    boolean plan = a != null && ChatAuth.hasPlan(a);
    boolean geminiOn = engine.usesGemini();
    accountStatus.setText(geminiOn ? "Gemini" : plan ? a.optString("email", "ChatGPT account") : "Local commands");
    accountStatus.setTextColor(geminiOn || plan ? GOOD : WARN);
    accountMeta.setText(
        (geminiOn ? engine.geminiModel() + " · " : plan ? "ChatGPT plan · " : "No AI connection · ")
            + tools.names().size()
            + " tools · navigation "
            + (NavigationService.instance == null ? "off" : "on"));
    aiChip.setText("AI 0/" + Math.max(1, Math.min(12, prefs.getInt("max_rounds", 4))));
    aiChip.setTextColor(MUTED);
    refreshToolChip();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 902) return;
    if (result != RESULT_OK || data == null) {
      status("Screen vision was not enabled");
      return;
    }
    Intent service = new Intent(this, CaptureService.class).putExtra("result", result).putExtra("grant", data);
    if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service);
    else startService(service);
    handler.postDelayed(
        () ->
            assistant(
                CaptureService.instance != null
                    ? "Screen vision enabled. AI can now inspect the TV during your tasks."
                    : "Screen vision could not start. " + CaptureService.lastError),
        1200);
  }

  // ---------------------------------------------------------------- view helpers

  private void interactive(View v, GradientDrawable rest, GradientDrawable lit) {
    v.setBackground(rest);
    v.setFocusable(true);
    v.setOnFocusChangeListener((view, hasFocus) -> view.setBackground(hasFocus ? lit : rest));
  }

  private TextView text(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private GradientDrawable panel(int color, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(12));
    if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
    return d;
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onDestroy() {
    if (micProbe != null) micProbe.cancel();
    if (auth != null) auth.cancelLogin();
    if (engine != null) engine.shutdown();
    if (tools != null) tools.close();
    if (tts != null) tts.shutdown();
    super.onDestroy();
  }
}