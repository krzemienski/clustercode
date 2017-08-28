package net.chrigel.clustercode.process.impl;

import lombok.extern.slf4j.XSlf4j;
import net.chrigel.clustercode.process.ExternalProcess;
import net.chrigel.clustercode.process.OutputParser;
import net.chrigel.clustercode.process.RunningExternalProcess;
import net.chrigel.clustercode.util.Platform;
import org.slf4j.ext.XLogger;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Wait for Java 9, the implementation of external process will be much more improved. yay :)
 */
@XSlf4j
class ProcessImpl implements ExternalProcess, RunningExternalProcess {

    private final ScheduledExecutorService executor;
    private boolean redirectIO;
    private Path executable;
    private Optional<List<String>> arguments;
    private Optional<Process> subprocess;
    private Optional<Path> workingDir;
    private boolean logSuppressed;
    private OutputParser<?> stdParser;
    private OutputParser<?> errParser;

    ProcessImpl() {
        this.arguments = Optional.empty();
        this.workingDir = Optional.empty();
        this.subprocess = Optional.empty();
        this.executor = Executors.newScheduledThreadPool(2);
    }

    @Override
    public ExternalProcess withIORedirected(boolean redirectIO) {
        this.redirectIO = redirectIO;
        return this;
    }

    @Override
    public ExternalProcess withStdoutParser(OutputParser<?> stdParser) {
        this.stdParser = stdParser;
        return this;
    }

    @Override
    public ExternalProcess withStderrParser(OutputParser<?> errParser) {
        this.errParser = errParser;
        return this;
    }

    @Override
    public ExternalProcess withArguments(List<String> arguments) {
        this.arguments = Optional.of(arguments);
        return this;
    }

    @Override
    public ExternalProcess withExecutablePath(Path path) {
        this.executable = path;
        return this;
    }

    @Override
    public ExternalProcess withCurrentWorkingDirectory(Path path) {
        this.workingDir = Optional.of(path);
        return this;
    }

    @Override
    public ExternalProcess withLogSuppressed() {
        this.logSuppressed = true;
        return this;
    }

    @Override
    public Optional<Integer> start() {
        ProcessBuilder builder = new ProcessBuilder(buildArguments());
        if (redirectIO) {
            builder.inheritIO();
        } else if (Platform.currentPlatform() == Platform.WINDOWS) {
            // This is necessary. Otherwise waitFor() will be deadlocked even if the process finished hours ago.
            //builder.redirectError(ProcessBuilder.Redirect.appendTo(new File("NUL:")));
            //builder.redirectOutput(ProcessBuilder.Redirect.appendTo(new File("NUL:")));
            builder.redirectErrorStream(true);
        }
        workingDir.ifPresent(workingDir -> builder.directory(workingDir.toFile()));
        if (logSuppressed) {
            log.debug("Invoking external program...");
        } else {
            log.debug("Invoking: {}", builder.command());
        }
        try {
            Process process = builder.start();
            this.subprocess = Optional.of(process);
            if (this.stdParser != null) captureStream(process.getInputStream(), this.stdParser);
            if (this.errParser != null && Platform.currentPlatform() != Platform.WINDOWS)
                captureStream(process.getErrorStream(), this.errParser);
            return log.exit(Optional.of(subprocess.get().waitFor()));
        } catch (IOException e) {
            log.catching(e);
            this.subprocess = Optional.empty();
            return log.exit(Optional.empty());
        } catch (InterruptedException e) {
            log.catching(XLogger.Level.WARN, e);
            return log.exit(Optional.empty());
        }
    }

    private void captureStream(InputStream stream, OutputParser<?> parser) {
        CompletableFuture.runAsync(() -> {
            parser.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parser.parse(line);
                }
            } catch (IOException ex) {
                log.catching(XLogger.Level.WARN, ex);
            }
            parser.stop();
        });
    }

    private List<String> buildArguments() {
        List<String> args = new LinkedList<>();
        args.add(executable.toString());
        arguments.ifPresent(args::addAll);
        return args;
    }

    @Override
    public RunningExternalProcess start(Consumer<Optional<Integer>> listener) {
        this.executor.execute(() -> {
            listener.accept(start());
            log.info("Process terminated.");
            if (!executor.isShutdown()) {
                executor.shutdown();
            }
        });
        return this;
    }

    @Override
    public RunningExternalProcess sleep(long millis) {
        return sleep(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public RunningExternalProcess sleep(long timeout, TimeUnit unit) {
        try {
            Thread.sleep(unit.toMillis(timeout));
        } catch (InterruptedException e) {
            log.catching(XLogger.Level.WARN, e);
        }
        return this;
    }

    @Override
    public void destroyAfter(long millis) {
        destroyAfter(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroyAfter(long timeout, TimeUnit unit) {
        this.executor.schedule(() -> subprocess.ifPresent(
                process -> {
                    log.info("Destroying external process...");
                    process.destroyForcibly();
                    executor.shutdownNow();
                }),
                timeout, unit);
    }

    @Override
    public void awaitDestruction() {
        subprocess.ifPresent(process -> {
            try {
                log.debug("Waiting for process to destroy...");
                process.destroyForcibly().waitFor();
            } catch (InterruptedException e) {
                log.throwing(e);
                throw new RuntimeException(e);
            } finally {
                executor.shutdown();
            }
        });
    }

    @Override
    public boolean destroyNowWithTimeout(long timeout, TimeUnit unit) {
        if (subprocess.isPresent()) {
            try {
                log.debug("Waiting for process to destroy with timeout...");
                return subprocess.get().destroyForcibly().waitFor(timeout, unit);
            } catch (InterruptedException e) {
                log.throwing(e);
                throw new RuntimeException(e);
            } finally {
                executor.shutdownNow();
            }
        } else {
            return true;
        }
    }
}
