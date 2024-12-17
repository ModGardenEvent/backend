package net.modgarden.backend.event;

import java.util.List;

public record Event(long id,
                    String name,
                    List<Project> projects) {

}
