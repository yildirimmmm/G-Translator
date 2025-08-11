package translation;

import extension.TranslatorExtension;
import translation.translators.ArgosOpenTechTranslator;
import translation.translators.MicrosoftTranslator;
import translation.translators.DeepLTranslator;

public class TranslatorFactory {

    public static Translator get(TranslatorExtension t) {
        String api = t.getApi();

        if (api.equals("microsoft")) {
            return new MicrosoftTranslator(t.getMicrosoftKey(), t.getMicrosoftRegion());
        } else if (api.equals("deepl")) {
            return new DeepLTranslator(t.getDeepLKey());
        } else {
            return new ArgosOpenTechTranslator();
        }
    }

}
