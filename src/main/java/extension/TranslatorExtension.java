package extension;

import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.HEntity;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import misc.MaybeConsumer;
import translation.TranslationException;
import translation.Translator;
import translation.TranslatorFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

//todo:
// * group names


@ExtensionInfo(
        Title =  "G-Translator",
        Description =  "Translate the hotel",
        Version =  "1.1.0",
        Author =  "sirjonasxx & Tripical"
)
public class TranslatorExtension extends ExtensionForm {

    public Pane apiMicrosoftInfo;
    public TextField apiMicrosoftKey;
    public TextField apiMicrosoftRegion;
    public Pane apiDeepLInfo;
    public TextField apiDeepLKey;
    public RadioButton rdArgos;
    public RadioButton rdMicrosoft;
    public RadioButton rdDeepL;
    public ToggleGroup tglAPI;

    private volatile int userId = -1;
    private HashMap<Integer, HEntity> users = new HashMap<>();

    public ComboBox<Language> myLang;
    public ComboBox<Language> sourceLang;

    public CheckBox translateIncoming;
    public CheckBox translateOutgoing;
    public CheckBox translateWired;
    public CheckBox showOriginal;

    public CheckBox translateRoomInfo;
    public CheckBox translateNavigator;

    public CheckBox translateChatIn;
    public CheckBox translateChatOut;

    public Button isActiveBtn;

    private volatile boolean isActive = false;


    private SettingsManager settingsManager;
    private Map<String, Object> settings = new ConcurrentHashMap<>();

    public void initialize() {
        myLang.getItems().addAll(Language.values());
        sourceLang.getItems().addAll(Language.values());

        settingsManager = new SettingsManager(new File("."));
        settings.putAll(settingsManager.load());

        myLang.getSelectionModel().selectFirst();
        sourceLang.getSelectionModel().selectFirst();

        hideAllApiPanels();

        myLang.valueProperty().addListener((o,ov,nv) -> { settings.put("myLang", nv.name()); persist(); });
        sourceLang.valueProperty().addListener((o,ov,nv) -> { settings.put("srcLang", nv.name()); persist(); });

        addCheckboxListener(showOriginal, "showOriginal");
        addCheckboxListener(translateIncoming, "trIn");
        addCheckboxListener(translateOutgoing, "trOut");
        addCheckboxListener(translateWired, "trWired");
        addCheckboxListener(translateRoomInfo, "trRoomInfo");
        addCheckboxListener(translateNavigator, "trNav");
        addCheckboxListener(translateChatIn, "trChatIn");
        addCheckboxListener(translateChatOut, "trChatOut");

        apiMicrosoftKey.textProperty().addListener((o,ov,nv) -> { settings.put("msKey", nv); persist(); });
        apiMicrosoftRegion.textProperty().addListener((o,ov,nv) -> { settings.put("msRegion", nv); persist(); });
        apiDeepLKey.textProperty().addListener((o,ov,nv) -> { settings.put("deeplKey", nv); persist(); });

        rdMicrosoft.selectedProperty().addListener((obs,old,val) -> { if (val) { showMicrosoft(); settings.put("api", "microsoft"); persist(); } });
        rdDeepL.selectedProperty().addListener((obs,old,val) -> { if (val) { showDeepL(); settings.put("api", "deepl"); persist(); } });
        rdArgos.selectedProperty().addListener((obs,old,val) -> { if (val) { hideAllApiPanels(); settings.put("api", "argos"); persist(); } });

        applySavedState();
    }

    private void applySavedState() {
        String myLangSaved = (String) settings.get("myLang");
        if (myLangSaved != null) safeSelectLang(myLang, myLangSaved);
        String srcLangSaved = (String) settings.get("srcLang");
        if (srcLangSaved != null) safeSelectLang(sourceLang, srcLangSaved);

        apiMicrosoftKey.setText((String) settings.getOrDefault("msKey", ""));
        apiMicrosoftRegion.setText((String) settings.getOrDefault("msRegion", "global"));
        apiDeepLKey.setText((String) settings.getOrDefault("deeplKey", ""));

        setIfPresent(showOriginal, "showOriginal");
        setIfPresent(translateIncoming, "trIn");
        setIfPresent(translateOutgoing, "trOut");
        setIfPresent(translateWired, "trWired");
        setIfPresent(translateRoomInfo, "trRoomInfo");
        setIfPresent(translateNavigator, "trNav");
        setIfPresent(translateChatIn, "trChatIn");
        setIfPresent(translateChatOut, "trChatOut");

        String api = (String) settings.getOrDefault("api", "argos");
        switch (api) {
            case "microsoft": rdMicrosoft.setSelected(true); break;
            case "deepl": rdDeepL.setSelected(true); break;
            default: rdArgos.setSelected(true); break;
        }
    }

    private void safeSelectLang(ComboBox<Language> box, String name) {
        try { box.getSelectionModel().select(Language.valueOf(name)); } catch (Exception ignored) {}
    }

    private void hideAllApiPanels() {
        apiMicrosoftInfo.setVisible(false); apiMicrosoftInfo.setManaged(false); apiMicrosoftInfo.setDisable(true);
        apiDeepLInfo.setVisible(false); apiDeepLInfo.setManaged(false); apiDeepLInfo.setDisable(true);
    }
    private void showMicrosoft() { hideAllApiPanels(); apiMicrosoftInfo.setVisible(true); apiMicrosoftInfo.setManaged(true); apiMicrosoftInfo.setDisable(false); }
    private void showDeepL() { hideAllApiPanels(); apiDeepLInfo.setVisible(true); apiDeepLInfo.setManaged(true); apiDeepLInfo.setDisable(false); }

    private void addCheckboxListener(CheckBox box, String key) { box.selectedProperty().addListener((o,ov,nv) -> { settings.put(key, nv); persist(); }); }
    private void setIfPresent(CheckBox box, String key) { Object v = settings.get(key); if (v instanceof Boolean) box.setSelected((Boolean)v); }
    private void persist() { settingsManager.save(settings); }

    public String getDeepLKey() {
        return apiDeepLKey.getText();
    }


    @Override
    protected void initExtension() {
        intercept(HMessage.Direction.TOCLIENT, "Chat", this::onReceiveChat);
        intercept(HMessage.Direction.TOCLIENT, "Whisper", this::onReceiveChat);
        intercept(HMessage.Direction.TOCLIENT, "Shout", this::onReceiveChat);

        intercept(HMessage.Direction.TOSERVER, "Chat", this::onSendChat);
        intercept(HMessage.Direction.TOSERVER, "Whisper", this::onSendChat);
        intercept(HMessage.Direction.TOSERVER, "Shout", this::onSendChat);

        intercept(HMessage.Direction.TOCLIENT, "RoomReady", this::onRoomReady);
        intercept(HMessage.Direction.TOCLIENT, "UserObject", this::onInfoRetrieve);
        intercept(HMessage.Direction.TOCLIENT, "Users", this::onUsers);

        intercept(HMessage.Direction.TOSERVER, "SendMsg", this::onSendDM);
        intercept(HMessage.Direction.TOSERVER, "SendRoomInvite", this::onSendInvitation);
        intercept(HMessage.Direction.TOCLIENT, "NewConsole", this::onReceiveDMOrInvitation);
        intercept(HMessage.Direction.TOCLIENT, "RoomInvite", this::onReceiveDMOrInvitation);

        intercept(HMessage.Direction.TOCLIENT, "GetGuestRoomResult", this::onRoomInfo);
        intercept(HMessage.Direction.TOCLIENT, "NavigatorSearchResultBlocks", this::onNavigatorResult);

        sendToServer(new HPacket("GetHeightMap", HMessage.Direction.TOSERVER));
        sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
    }

    private void onNavigatorResult(HMessage hMessage) {
        Translator translator = TranslatorFactory.get(this);
        if (isActive && translateNavigator.isSelected() && translator.allowMultiLines()) {
            hMessage.setBlocked(true);

            List<Integer> translationIndexes = new ArrayList<>();
            List<String> translations = new ArrayList<>();

            HPacket packet = hMessage.getPacket();
            packet.readString();
            packet.readString();

            int blocks = packet.readInteger();
            for (int i = 0; i < blocks; i++) {
                packet.readString();

                packet.readString();
//                translationIndexes.add(packet.getReadIndex());
//                translations.add(packet.readString(StandardCharsets.UTF_8));

                packet.readInteger();
                packet.readBoolean();
                packet.readInteger();

                int rooms = packet.readInteger();
                for (int j = 0; j < rooms; j++) {
                    // ty wiredspast
                    //        self.flatId, self.roomName, self.ownerId, self.ownerName, self.doorMode
                    //        , self.userCount, self.maxUserCount, self.description, self.tradeMode,
                    //        self.score, self.ranking, self.categoryId = packet.read('isisiiisiiii')

                    packet.readInteger();

                    translationIndexes.add(packet.getReadIndex());
                    translations.add(packet.readString(StandardCharsets.UTF_8));

                    packet.readInteger();
                    packet.readString();
                    packet.readInteger();
                    packet.readInteger();
                    packet.readInteger();
                    packet.readString();    // dont translate description
                    packet.readInteger();
                    packet.readInteger();
                    packet.readInteger();
                    packet.readInteger();

                    int tags = packet.readInteger();
                    for (int k = 0; k < tags; k++) {
                        packet.readString();
                    }

                    int multiUse = packet.readInteger();
                    if ((multiUse & 1) > 0) // official room
                        packet.readString();

                    if ((multiUse & 2) > 0) { // group
                        packet.readInteger();
                        packet.readString();
                        packet.readString();
                    }

                    if ((multiUse & 4) > 0) { // room ad
                        translationIndexes.add(packet.getReadIndex());
                        translations.add(packet.readString(StandardCharsets.UTF_8));

                        packet.readString();
                        packet.readInteger();
                    }
                }
            }

            translator.translate(translations, getSourceLanguage(), getMyLanguage(), new MaybeConsumer<List<String>, TranslationException>() {
                @Override
                public void except(TranslationException exception) {
                    System.out.println(exception.getReason());
                }

                @Override
                public void accept(List<String> strings) {
                    for (int i = strings.size() - 1; i >= 0; i--) {
                        int index = translationIndexes.get(i);
                        String replacedText = strings.get(i);
                        packet.replaceString(index, replacedText);
                    }
                    sendToClient(packet);
                }
            });

        }
    }

    private void onReceiveDMOrInvitation(HMessage hMessage) {
    if (isActive && translateChatIn.isSelected()) {
            HPacket packet = hMessage.getPacket();

            packet.readInteger(); // sender
            String text = packet.readString(StandardCharsets.UTF_8);

            hMessage.setBlocked(true);

            Translator translator = TranslatorFactory.get(this);
            translator.translate(text, getSourceLanguage(), getMyLanguage(), new MaybeConsumer<String, TranslationException>() {
                @Override
                public void except(TranslationException exception) {
                    System.out.println(exception.getReason());
                }

                @Override
                public void accept(String s) {
                    packet.replaceString(10, s, StandardCharsets.UTF_8);
                    sendToClient(packet);
                }
            });

        }
    }

    private void onSendDM(HMessage hMessage) {
        if (isActive && translateChatOut.isSelected()) {
            HPacket packet = hMessage.getPacket();

            packet.readInteger(); // receiver
            String text = packet.readString(StandardCharsets.UTF_8);

            hMessage.setBlocked(true);

            Translator translator = TranslatorFactory.get(this);
            translator.translate(text, getMyLanguage(), getSourceLanguage(), new MaybeConsumer<String, TranslationException>() {
                @Override
                public void except(TranslationException exception) {
                    System.out.println(exception.getReason());
                }

                @Override
                public void accept(String s) {
                    packet.replaceString(10, s, StandardCharsets.UTF_8);
                    sendToServer(packet);
                }
            });

        }
    }

    private void onSendInvitation(HMessage hMessage) {
        if (isActive && translateChatOut.isSelected()) {
            HPacket packet = hMessage.getPacket();

            int amountReceivers = packet.readInteger();
            int textIndexInPacket = 6 + 4 + amountReceivers * 4;
            String text = packet.readString(textIndexInPacket, StandardCharsets.UTF_8);

            hMessage.setBlocked(true);

            Translator translator = TranslatorFactory.get(this);
            translator.translate(text, getMyLanguage(), getSourceLanguage(), new MaybeConsumer<String, TranslationException>() {
                @Override
                public void except(TranslationException exception) {
                    System.out.println(exception.getReason());
                }

                @Override
                public void accept(String s) {
                    packet.replaceString(textIndexInPacket, s, StandardCharsets.UTF_8);
                    sendToServer(packet);
                }
            });

        }
    }

    private void onRoomInfo(HMessage hMessage) {
        if (isActive && translateRoomInfo.isSelected()) {
            HPacket packet = hMessage.getPacket();

            boolean test = packet.readBoolean();
            if (!test) return;

            hMessage.setBlocked(true);
            int roomId = packet.readInteger();
			
            int roomNamePacketIndex = packet.getReadIndex();
            String originalRoomName = packet.readString(StandardCharsets.UTF_8);

            packet.readInteger();
            packet.readString();
            packet.readInteger();
            packet.readInteger();
            packet.readInteger();

            int roomDescPacketIndex = packet.getReadIndex();
            String originalDesc = packet.readString(StandardCharsets.UTF_8);

            TranslatorFactory.get(this).translate(Arrays.asList(originalRoomName, originalDesc), getSourceLanguage(), getMyLanguage(), new MaybeConsumer<List<String>, TranslationException>() {
                @Override
                public void except(TranslationException exception) {
                    System.out.println(exception.getReason());
                }

                @Override
                public void accept(List<String> s) {
                    packet.replaceString(roomDescPacketIndex, s.get(1), StandardCharsets.UTF_8);
                    packet.replaceString(roomNamePacketIndex, s.get(0), StandardCharsets.UTF_8);
                    sendToClient(packet);
                }
            });
        }
    }

    private void onUsers(HMessage hMessage) {
        HEntity[] hEntities = HEntity.parse(hMessage.getPacket());
        for(HEntity hEntity : hEntities) {
            users.put(hEntity.getIndex(), hEntity);
        }
    }

    private void onInfoRetrieve(HMessage hMessage) {
        userId = hMessage.getPacket().readInteger();
    }

    private void onRoomReady(HMessage hMessage) {
        users.clear();
        if (userId == -1) {
            sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
        }
    }

    private boolean userIsYou(int index) {
        return !users.containsKey(index) || (userId != -1 && users.get(index).getId() == userId);
    }

    @Override
    protected void onEndConnection() {
        userId = -1;
    }

    private Language getMyLanguage() {
        return myLang.getValue();
    }

    private Language getSourceLanguage() {
        return sourceLang.getValue();
    }

    private void onSendChat(HMessage hMessage) {
        boolean isWhisper = getPacketInfoManager().getPacketInfoFromHeaderId(HMessage.Direction.TOSERVER, hMessage.getPacket().headerId()).getName().equals("Whisper");

        if (isActive && translateOutgoing.isSelected()) {
            HPacket packet = hMessage.getPacket();
            String text = packet.readString(StandardCharsets.UTF_8);

            if (showOriginal.isSelected()) {
                sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, -1, text, 0, 30, 0, -1));
            }

            hMessage.setBlocked(true);

            Translator translator = TranslatorFactory.get(this);

            String receiver = isWhisper ? text.split(" ")[0] : null;
            text = isWhisper ? text.substring(receiver.length() + 1) : text;

            translator.translate(text, getMyLanguage(), getSourceLanguage(), new MaybeConsumer<String, TranslationException>() {
                @Override
                public void except(TranslationException exception) {
                    System.out.println(exception.getReason());
                }

                @Override
                public void accept(String s) {
                    packet.replaceString(6, isWhisper ? receiver + " " + s : s, StandardCharsets.UTF_8);
                    sendToServer(packet);
                }
            });
        }
    }

    private void onReceiveChat(HMessage hMessage) {
        if (isActive && translateIncoming.isSelected()) {
            HPacket packet = hMessage.getPacket();
            HPacket copy = new HPacket(packet);

            int userIndex = packet.readInteger();
            String text = packet.readString(StandardCharsets.UTF_8);
            packet.readInteger();
            int chatBubble = packet.readInteger();

            boolean isWiredMessage = chatBubble == 34;
            if ((isWiredMessage && !translateWired.isSelected()) || (!isWiredMessage && userIsYou(userIndex))) return;

            if (showOriginal.isSelected()) {
                packet.replaceInt(packet.getReadIndex() - 4, isWiredMessage ? 33 : 30);
            }
            else {
                hMessage.setBlocked(true);
            }

            Translator translator = TranslatorFactory.get(this);
            translator.translate(text, getSourceLanguage(), getMyLanguage(), new MaybeConsumer<String, TranslationException>() {
                @Override
                public void except(TranslationException exception) {
                    System.out.println(exception.getReason());
                }

                @Override
                public void accept(String s) {
                    copy.resetReadIndex();
                    copy.readInteger();
                    copy.replaceString(copy.getReadIndex(), s, StandardCharsets.UTF_8);
                    sendToClient(copy);
                }
            });

        }
    }

    public void startStop(ActionEvent actionEvent) {
        if (isActive) {
            isActive = false;
            isActiveBtn.setText("Start");
        }
        else {
            isActive = true;
            isActiveBtn.setText("Stop");
        }
    }

    public String getApi() {
        return ((RadioButton) (tglAPI.getSelectedToggle())).getText().split(" ")[0].toLowerCase();
    }

    public String getMicrosoftKey() {
        return apiMicrosoftKey.getText();
    }

    public String getMicrosoftRegion() {
        return apiMicrosoftRegion.getText();
    }
}
