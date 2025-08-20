package com.here.naksha.cli.copy.service;

import java.util.List;

public record CopyServiceSuccessResultPayload(int numberOfCopiedElements, List<String> messages) {
}
