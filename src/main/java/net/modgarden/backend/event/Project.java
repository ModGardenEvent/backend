package net.modgarden.backend.event;

import java.util.List;

public record Project(long modrinthId,
                      List<Long> authorIds,
                      List<Event> associatedEvents) {

}
