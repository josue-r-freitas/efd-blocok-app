package br.com.triumph.efd.io;

import br.com.triumph.efd.model.EfdFile;
import br.com.triumph.efd.util.SpedLineUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class EfdReader {

    public EfdFile read(Path path) throws IOException {
        EfdFile efd = new EfdFile();
        Charset charset = detectCharset(path);
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String line;
            boolean encerrado = false;
            int ignoradas = 0;
            while ((line = reader.readLine()) != null) {
                if (encerrado) {
                    ignoradas++;
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                String trimmed = line.trim();
                if (!SpedLineUtils.isLinhaSped(trimmed)) {
                    ignoradas++;
                    continue;
                }
                efd.addLine(trimmed);
                if (SpedLineUtils.isEncerramentoArquivo(trimmed)) {
                    encerrado = true;
                }
            }
            if (ignoradas > 0) {
                // assinatura digital ou lixo após |9999|
            }
        }
        if (efd.size() == 0) {
            throw new IOException("Arquivo EFD vazio: " + path);
        }
        String first = efd.getLines().get(0);
        if (!first.startsWith("|0000|")) {
            throw new IOException("Arquivo inválido: esperado registro 0000 no início.");
        }
        return efd;
    }

    public void write(Path path, List<String> lines) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1)) {
            for (String line : lines) {
                writer.write(line);
                writer.write("\r\n");
            }
        }
    }

    private Charset detectCharset(Path path) throws IOException {
        byte[] sample = Files.readAllBytes(path);
        // Prefer ISO-8859-1 for SPED files (common in Brazil)
        String asLatin1 = new String(sample, 0, Math.min(sample.length, 4096), StandardCharsets.ISO_8859_1);
        if (asLatin1.contains("|0000|")) {
            return StandardCharsets.ISO_8859_1;
        }
        return StandardCharsets.UTF_8;
    }
}
