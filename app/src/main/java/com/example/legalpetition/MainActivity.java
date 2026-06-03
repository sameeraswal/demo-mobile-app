package com.example.legalpetition;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(30, 79, 219);
    private static final int LIGHT_BLUE = Color.rgb(220, 230, 255);
    private static final int LIGHT_GREY = Color.rgb(244, 246, 248);
    private static final int TEXT_DARK = Color.rgb(30, 35, 44);
    private static final int TEXT_MUTED = Color.rgb(99, 110, 124);

    private static final Prompt[] PROMPTS = new Prompt[] {
            new Prompt("parties", "Who are the parties involved? Include names, addresses if available, and whether each person is the petitioner/applicant or respondent/opposite party."),
            new Prompt("court", "Which court, tribunal, or jurisdiction should this petition be addressed to?"),
            new Prompt("facts", "Describe the important facts in chronological order. Add dates, places, amounts, and events wherever possible."),
            new Prompt("relief", "What order or relief do you want the court to grant?"),
            new Prompt("documents", "List the documents, evidence, notices, agreements, receipts, or records that support your case."),
            new Prompt("urgency", "Mention any limitation deadline, urgent hearing need, interim relief, or risk of immediate harm.")
    };

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final List<CheckBox> categoryBoxes = new ArrayList<>();
    private final Map<String, String> detailAnswers = new LinkedHashMap<>();

    private LinearLayout chatContainer;
    private LinearLayout paymentPlansContainer;
    private LinearLayout petitionPanel;
    private ScrollView chatScroll;
    private TextView sessionStatus;
    private TextView petitionOutput;
    private EditText messageInput;
    private Button sendButton;
    private Button startChatButton;
    private Button generatePetitionButton;
    private Button copyPetitionButton;
    private Button sharePetitionButton;

    private Runnable timerRunnable;
    private int remainingSeconds;
    private String activeSessionLabel = "Free chat";
    private boolean chatLocked;
    private boolean guidedChatStarted;
    private int currentPromptIndex;
    private String latestPetition = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        addMessage("Assistant", "Welcome. Select one or more legal case categories, then start the guided chat. I will collect facts and prepare a court petition draft.", false);
        startFreeSession();
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(12));
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(TEXT_DARK);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView disclaimer = new TextView(this);
        disclaimer.setText(getString(R.string.disclaimer));
        disclaimer.setTextColor(TEXT_MUTED);
        disclaimer.setTextSize(13);
        disclaimer.setPadding(0, dp(6), 0, dp(8));
        root.addView(disclaimer);

        TextView categoryLabel = label("Choose case categories");
        root.addView(categoryLabel);

        HorizontalScrollView categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryScroll.addView(categoryRow);
        root.addView(categoryScroll);
        addCategoryOptions(categoryRow);

        startChatButton = primaryButton("Start guided chat");
        startChatButton.setOnClickListener(v -> beginGuidedChat());
        root.addView(startChatButton, fullWidthParams(dp(8), dp(6), 0, dp(8)));

        sessionStatus = new TextView(this);
        sessionStatus.setTextColor(TEXT_DARK);
        sessionStatus.setTextSize(14);
        sessionStatus.setTypeface(Typeface.DEFAULT_BOLD);
        sessionStatus.setPadding(dp(12), dp(8), dp(12), dp(8));
        sessionStatus.setBackground(panelBackground(LIGHT_BLUE, BLUE));
        root.addView(sessionStatus);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(0, dp(10), 0, dp(10));
        chatScroll.addView(chatContainer);
        root.addView(chatScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        paymentPlansContainer = new LinearLayout(this);
        paymentPlansContainer.setOrientation(LinearLayout.VERTICAL);
        paymentPlansContainer.setVisibility(View.GONE);
        paymentPlansContainer.setPadding(dp(12), dp(10), dp(12), dp(10));
        paymentPlansContainer.setBackground(panelBackground(Color.rgb(255, 249, 226), Color.rgb(219, 164, 30)));
        root.addView(paymentPlansContainer, fullWidthParams(0, 0, 0, dp(8)));
        addPaymentPlanViews();

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(inputRow);

        messageInput = new EditText(this);
        messageInput.setHint("Type case details...");
        messageInput.setMinLines(1);
        messageInput.setMaxLines(4);
        messageInput.setTextColor(TEXT_DARK);
        messageInput.setHintTextColor(TEXT_MUTED);
        inputRow.addView(messageInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = primaryButton("Send");
        sendButton.setOnClickListener(v -> sendUserMessage());
        inputRow.addView(sendButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(actionRow, fullWidthParams(0, dp(8), 0, dp(6)));

        generatePetitionButton = secondaryButton("Generate petition");
        generatePetitionButton.setOnClickListener(v -> generatePetition());
        actionRow.addView(generatePetitionButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button resetButton = secondaryButton("Reset case");
        resetButton.setOnClickListener(v -> resetCaseDetails());
        actionRow.addView(resetButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        petitionPanel = new LinearLayout(this);
        petitionPanel.setOrientation(LinearLayout.VERTICAL);
        petitionPanel.setPadding(dp(12), dp(10), dp(12), dp(10));
        petitionPanel.setBackground(panelBackground(LIGHT_GREY, Color.rgb(213, 218, 226)));
        petitionPanel.setVisibility(View.GONE);
        root.addView(petitionPanel, fullWidthParams(0, 0, 0, 0));

        TextView petitionTitle = label("Generated petition draft");
        petitionPanel.addView(petitionTitle);

        ScrollView petitionScroll = new ScrollView(this);
        petitionOutput = new TextView(this);
        petitionOutput.setTextColor(TEXT_DARK);
        petitionOutput.setTextSize(14);
        petitionOutput.setLineSpacing(0, 1.08f);
        petitionScroll.addView(petitionOutput);
        petitionPanel.addView(petitionScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));

        LinearLayout petitionActions = new LinearLayout(this);
        petitionActions.setOrientation(LinearLayout.HORIZONTAL);
        petitionPanel.addView(petitionActions, fullWidthParams(0, dp(8), 0, 0));

        copyPetitionButton = secondaryButton("Copy");
        copyPetitionButton.setOnClickListener(v -> copyPetition());
        petitionActions.addView(copyPetitionButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        sharePetitionButton = secondaryButton("Share");
        sharePetitionButton.setOnClickListener(v -> sharePetition());
        petitionActions.addView(sharePetitionButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void addCategoryOptions(LinearLayout categoryRow) {
        String[] categories = getResources().getStringArray(R.array.legal_case_categories);
        for (String category : categories) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(category);
            checkBox.setTextColor(TEXT_DARK);
            checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
            checkBox.setPadding(dp(8), 0, dp(12), 0);
            categoryBoxes.add(checkBox);
            categoryRow.addView(checkBox);
        }
    }

    private void addPaymentPlanViews() {
        TextView title = label("Free chat ended. Choose a paid plan to continue.");
        paymentPlansContainer.addView(title);

        int[] prices = getResources().getIntArray(R.array.payment_plan_prices_rupees);
        int[] durations = getResources().getIntArray(R.array.payment_plan_durations_minutes);
        int planCount = Math.min(prices.length, durations.length);
        for (int i = 0; i < planCount; i++) {
            int price = prices[i];
            int duration = durations[i];
            Button planButton = secondaryButton("Pay Rs. " + price + " for " + duration + " mins");
            planButton.setOnClickListener(v -> activatePaidSession(price, duration));
            paymentPlansContainer.addView(planButton, fullWidthParams(0, dp(6), 0, 0));
        }
    }

    private void startFreeSession() {
        int freeSeconds = getResources().getInteger(R.integer.free_chat_duration_seconds);
        startSession("Free chat", freeSeconds);
    }

    private void startSession(String label, int seconds) {
        activeSessionLabel = label;
        remainingSeconds = Math.max(0, seconds);
        chatLocked = false;
        setChatControlsEnabled(true);
        paymentPlansContainer.setVisibility(View.GONE);
        timerHandler.removeCallbacksAndMessages(null);
        updateSessionStatus();

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (remainingSeconds <= 0) {
                    expireSession();
                    return;
                }
                remainingSeconds--;
                updateSessionStatus();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void updateSessionStatus() {
        sessionStatus.setText(String.format(Locale.US, "%s remaining: %02d:%02d",
                activeSessionLabel,
                remainingSeconds / 60,
                remainingSeconds % 60));
    }

    private void expireSession() {
        if (chatLocked) {
            return;
        }
        chatLocked = true;
        setChatControlsEnabled(false);
        updateSessionStatus();
        paymentPlansContainer.setVisibility(View.VISIBLE);
        addMessage("Assistant", activeSessionLabel + " has ended. Please select a payment plan to continue the chat and add more details.", false);
    }

    private void activatePaidSession(int price, int durationMinutes) {
        String label = "Paid chat (Rs. " + price + ")";
        startSession(label, durationMinutes * 60);
        addMessage("Assistant", "Payment plan activated: Rs. " + price + " for " + durationMinutes + " minutes. You can continue the chat now.", false);
    }

    private void setChatControlsEnabled(boolean enabled) {
        messageInput.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        startChatButton.setEnabled(enabled);
        messageInput.setHint(enabled ? "Type case details..." : "Select a payment plan to continue...");
    }

    private void beginGuidedChat() {
        if (chatLocked) {
            addMessage("Assistant", "Please select a payment plan to continue the chat.", false);
            return;
        }
        if (selectedCategories().isEmpty()) {
            addMessage("Assistant", "Please choose at least one legal case category first.", false);
            return;
        }
        if (!guidedChatStarted) {
            guidedChatStarted = true;
            addMessage("Assistant", "Selected categories: " + describeSelectedCategories() + ". I will ask a few questions to build the petition.", false);
            promptCurrentQuestion();
        } else {
            addMessage("Assistant", "The guided chat is already in progress. Please answer the current question or add more details.", false);
        }
    }

    private void sendUserMessage() {
        if (chatLocked) {
            addMessage("Assistant", "Your chat window has ended. Choose a paid plan to continue.", false);
            return;
        }

        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }

        addMessage("You", text, true);
        messageInput.setText("");
        hideKeyboard();

        if (selectedCategories().isEmpty()) {
            addMessage("Assistant", "I saved your message as context. Please choose at least one legal case category so I can structure the petition.", false);
            appendDetail("initial_context", text);
            return;
        }

        if (!guidedChatStarted) {
            guidedChatStarted = true;
            appendDetail("initial_context", text);
            addMessage("Assistant", "I captured that as initial context. Now I will collect the information needed for the petition.", false);
            promptCurrentQuestion();
            return;
        }

        if (currentPromptIndex < PROMPTS.length) {
            Prompt currentPrompt = PROMPTS[currentPromptIndex];
            appendDetail(currentPrompt.key, text);
            currentPromptIndex++;
            if (currentPromptIndex < PROMPTS.length) {
                promptCurrentQuestion();
            } else {
                addMessage("Assistant", "Thank you. I have enough information to prepare a petition draft. Tap \"Generate petition\" to review it, or continue adding extra details here.", false);
            }
        } else {
            appendDetail("additional_notes", text);
            addMessage("Assistant", "Added those details to the case notes. You can generate or regenerate the petition any time.", false);
        }
    }

    private void promptCurrentQuestion() {
        if (currentPromptIndex < PROMPTS.length) {
            addMessage("Assistant", PROMPTS[currentPromptIndex].question, false);
        }
    }

    private void generatePetition() {
        if (selectedCategories().isEmpty()) {
            addMessage("Assistant", "Choose at least one category before generating the petition.", false);
            return;
        }
        latestPetition = buildPetitionDraft();
        petitionOutput.setText(latestPetition);
        petitionPanel.setVisibility(View.VISIBLE);
        addMessage("Assistant", "The petition draft is ready below. Review every fact, date, legal provision, and prayer before filing.", false);
    }

    private String buildPetitionDraft() {
        String categories = describeSelectedCategories();
        String court = valueOrPlaceholder("court", "[Name of the court / tribunal]");
        String parties = valueOrPlaceholder("parties", "[Petitioner and respondent details]");
        String facts = valueOrPlaceholder("facts", "[Chronological facts of the case]");
        String relief = valueOrPlaceholder("relief", "[Specific reliefs requested from the court]");
        String documents = valueOrPlaceholder("documents", "[List of supporting documents]");
        String urgency = valueOrPlaceholder("urgency", "[Urgency, limitation, and interim relief details]");
        String initialContext = detailAnswers.get("initial_context");
        String additionalNotes = detailAnswers.get("additional_notes");

        StringBuilder builder = new StringBuilder();
        builder.append("IN THE ").append(court).append("\n\n");
        builder.append(categories.toUpperCase(Locale.US)).append(" PETITION\n\n");
        builder.append("IN THE MATTER OF:\n").append(parties).append("\n\n");
        builder.append("PETITION UNDER THE APPROPRIATE PROVISIONS OF LAW\n");
        builder.append("FOR NECESSARY RELIEF IN RESPECT OF THE ABOVE MATTER\n\n");

        section(builder, "1. PARTICULARS OF PARTIES", parties);
        section(builder, "2. CASE CATEGORY", categories);
        if (initialContext != null && !initialContext.trim().isEmpty()) {
            section(builder, "3. INITIAL CASE SUMMARY", initialContext);
        }
        section(builder, "4. FACTS OF THE CASE", facts);
        section(builder, "5. GROUNDS", buildGrounds(categories, facts, urgency));
        section(builder, "6. DOCUMENTS RELIED UPON", documents);
        section(builder, "7. URGENCY / LIMITATION", urgency);
        section(builder, "8. PRAYER", relief);
        if (additionalNotes != null && !additionalNotes.trim().isEmpty()) {
            section(builder, "9. ADDITIONAL NOTES", additionalNotes);
        }

        builder.append("VERIFICATION\n");
        builder.append("I, the petitioner/applicant named above, verify that the contents of this petition are true and correct to the best of my knowledge and belief, and that no material fact has been concealed.\n\n");
        builder.append("Place: ____________________\n");
        builder.append("Date: _____________________\n");
        builder.append("Signature: ________________\n\n");
        builder.append("Note: This is a draft prepared from user-provided details. It should be reviewed and adapted by a qualified legal professional before court filing.");
        return builder.toString();
    }

    private String buildGrounds(String categories, String facts, String urgency) {
        StringBuilder grounds = new StringBuilder();
        grounds.append("a. The petitioner has a cause of action arising from the facts stated above.\n");
        grounds.append("b. The matter relates to the selected category/categories: ").append(categories).append(".\n");
        grounds.append("c. The documents and circumstances described by the petitioner support the relief sought.\n");
        grounds.append("d. The court may grant appropriate relief to prevent injustice and protect the petitioner's rights.\n");
        if (!facts.startsWith("[")) {
            grounds.append("e. The factual sequence provided indicates issues that require judicial consideration.\n");
        }
        if (!urgency.startsWith("[")) {
            grounds.append("f. The urgency or limitation details provided justify timely consideration of the matter.\n");
        }
        return grounds.toString();
    }

    private void section(StringBuilder builder, String title, String body) {
        builder.append(title).append("\n");
        builder.append(body).append("\n\n");
    }

    private void copyPetition() {
        if (latestPetition.trim().isEmpty()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Petition draft", latestPetition));
            Toast.makeText(this, "Petition copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePetition() {
        if (latestPetition.trim().isEmpty()) {
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Court petition draft");
        shareIntent.putExtra(Intent.EXTRA_TEXT, latestPetition);
        startActivity(Intent.createChooser(shareIntent, "Share petition draft"));
    }

    private void resetCaseDetails() {
        detailAnswers.clear();
        latestPetition = "";
        guidedChatStarted = false;
        currentPromptIndex = 0;
        for (CheckBox checkBox : categoryBoxes) {
            checkBox.setChecked(false);
        }
        chatContainer.removeAllViews();
        petitionPanel.setVisibility(View.GONE);
        addMessage("Assistant", "Case details have been reset. The active chat timer is unchanged.", false);
    }

    private void appendDetail(String key, String value) {
        String existing = detailAnswers.get(key);
        if (existing == null || existing.trim().isEmpty()) {
            detailAnswers.put(key, value);
        } else {
            detailAnswers.put(key, existing + "\n" + value);
        }
    }

    private String valueOrPlaceholder(String key, String placeholder) {
        String value = detailAnswers.get(key);
        if (value == null || value.trim().isEmpty()) {
            return placeholder;
        }
        return value.trim();
    }

    private List<String> selectedCategories() {
        List<String> selected = new ArrayList<>();
        for (CheckBox checkBox : categoryBoxes) {
            if (checkBox.isChecked()) {
                selected.add(checkBox.getText().toString());
            }
        }
        return selected;
    }

    private String describeSelectedCategories() {
        List<String> selected = selectedCategories();
        if (selected.isEmpty()) {
            return "Not selected";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(selected.get(i));
        }
        return builder.toString();
    }

    private void addMessage(String sender, String message, boolean fromUser) {
        TextView bubble = new TextView(this);
        bubble.setText(sender + "\n" + message);
        bubble.setTextSize(14);
        bubble.setTextColor(fromUser ? Color.WHITE : TEXT_DARK);
        bubble.setLineSpacing(0, 1.08f);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
        bubble.setBackground(panelBackground(fromUser ? BLUE : LIGHT_GREY, fromUser ? BLUE : Color.rgb(224, 229, 236)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = fromUser ? Gravity.END : Gravity.START;
        params.setMargins(fromUser ? dp(48) : 0, dp(4), fromUser ? 0 : dp(48), dp(4));
        chatContainer.addView(bubble, params);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(TEXT_DARK);
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        return label;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackground(panelBackground(BLUE, BLUE));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(BLUE);
        button.setBackground(panelBackground(Color.WHITE, BLUE));
        return button;
    }

    private GradientDrawable panelBackground(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidthParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && messageInput != null) {
            imm.hideSoftInputFromWindow(messageInput.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return (int) (value * Resources.getSystem().getDisplayMetrics().density + 0.5f);
    }

    private static final class Prompt {
        final String key;
        final String question;

        Prompt(String key, String question) {
            this.key = key;
            this.question = question;
        }
    }
}
