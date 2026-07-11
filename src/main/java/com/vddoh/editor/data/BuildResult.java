package com.vddoh.editor.data;

import java.nio.file.Path;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record BuildResult(Path outputJar, String summary) {}
