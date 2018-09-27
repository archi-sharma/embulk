package org.embulk.spi.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Optional;
import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.Task;
import org.embulk.spi.FileInput;

public class LineDecoder implements AutoCloseable, Iterable<String> {
    // TODO optimize

    public static interface DecoderTask extends Task {
        @Config("charset")
        @ConfigDefault("\"utf-8\"")
        public Charset getCharset();

        @Config("newline")
        @ConfigDefault("\"CRLF\"")
        public Newline getNewline();

        @Config("line_delimiter")
        @ConfigDefault("null")
        public Optional<Newline> getLineDelimiter();
    }

    private final FileInputInputStream inputStream;
    private final LineReader reader;
    private final Charset charset;

    public LineDecoder(FileInput in, DecoderTask task) {
        this.charset = task.getCharset();
        CharsetDecoder decoder = charset
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)  // TODO configurable?
                .onUnmappableCharacter(CodingErrorAction.REPLACE);  // TODO configurable?
        this.inputStream = new FileInputInputStream(in);
        this.reader = new LineReader(
                new InputStreamReader(inputStream, decoder), task.getLineDelimiter().orNull(), 256
        );
    }

    public boolean nextFile() {
        boolean has = inputStream.nextFile();
        if (has && charset.equals(UTF_8)) {
            skipBom();
        }
        return has;
    }

    private void skipBom() {
        boolean skip = false;
        try {
            if (charset.equals(UTF_8)) {
                reader.mark(3);
                int firstChar = reader.read();
                if (firstChar == 0xFEFF) {
                    // skip BOM bytes
                    skip = true;
                }
            }
        } catch (IOException ex) {
            // Passing through intentionally.
        } finally {
            if (skip) {
                // firstChar is skipped
            } else {
                // rollback to the marked position
                try {
                    reader.reset();
                } catch (IOException ex) {
                    // unexpected
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    public String poll() {
        try {
            return reader.readLine();
        } catch (IOException ex) {
            // unexpected
            throw new RuntimeException(ex);
        }
    }

    public void close() {
        try {
            reader.close();
        } catch (IOException ex) {
            // unexpected
            throw new RuntimeException(ex);
        }
    }

    public Iterator<String> iterator() {
        return new Ite(this);
    }

    private String nextLine;

    private static class Ite implements Iterator<String> {
        private LineDecoder self;

        public Ite(LineDecoder self) {
            // TODO non-static inner class causes a problem with JRuby
            this.self = self;
        }

        @Override
        public boolean hasNext() {
            if (self.nextLine != null) {
                return true;
            } else {
                self.nextLine = self.poll();
                return self.nextLine != null;
            }
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String l = self.nextLine;
            self.nextLine = null;
            return l;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * A {@link BufferedReader} that can specify line delimiter character from any one of CR, LF and CRLF.
     * If not specified, follow the original {@link BufferedReader}'s behavior.
     */
    private static class LineReader extends BufferedReader {
        private final Newline lineDelimiter;   // should create another enum?
        private final char[] buffer;
        private int offset = 0;
        private int charsRead = 0;

        LineReader(Reader reader, Newline lineDelimiter, int bufferSize) {
            super(reader);
            this.lineDelimiter = lineDelimiter;
            this.buffer = new char[bufferSize];
        }

        @Override
        public String readLine() throws IOException {
            if (lineDelimiter == null) {
                return super.readLine();
            }

            StringBuilder line = null;
            bufferLoop:
            while (offset > 0 || (charsRead = this.read(buffer)) != -1) {
                if (line == null) {
                    line = new StringBuilder(); // Initialize line's buffer for the first loop
                }
                for (int i = offset; i < charsRead; i++) {
                    char c = buffer[i];
                    boolean isEOL = false;
                    switch (lineDelimiter) {
                        case CR:
                            if (c == '\r') {
                                isEOL = true;
                            }
                            break;
                        case LF:
                            if (c == '\n') {
                                isEOL = true;
                            }
                            break;
                        case CRLF:
                            if ((line.length() > 0 && line.charAt(line.length() - 1) == '\r') && c == '\n') {
                                // Delete unnecessary CR
                                line.deleteCharAt(line.length() - 1);
                                isEOL = true;
                            }
                            break;
                    }
                    offset++;
                    if (isEOL) {
                        break bufferLoop;
                    }
                    line.append(c);
                }
                offset = 0;
            }

            if (line != null) {
                return line.toString();
            }
            return null;
        }
    }
}
