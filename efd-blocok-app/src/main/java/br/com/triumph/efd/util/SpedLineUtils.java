package br.com.triumph.efd.util;

import java.util.regex.Pattern;

public final class SpedLineUtils {

    private static final Pattern SPED_LINE = Pattern.compile("^\\|[A-Z0-9]+\\|.*");
    private static final Pattern REG_PATTERN = Pattern.compile("^\\|([^|]+)\\|");

    private SpedLineUtils() {
    }

    public static boolean isLinhaSped(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        return !trimmed.isEmpty() && SPED_LINE.matcher(trimmed).matches();
    }

    public static boolean isAssinaturaOuLixo(String line) {
        return !isLinhaSped(line);
    }

    public static String registro(String line) {
        if (line == null) {
            return null;
        }
        var m = REG_PATTERN.matcher(line.trim());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static boolean isEncerramentoArquivo(String line) {
        return "9999".equals(registro(line));
    }
}
