package com.vddoh.editor.service;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record HeroOffsets(int statOffset, int seedOffset) {}
