package net.chrigel.clustercode.cluster.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelTaskMessage implements ClusterMessage {

    private String hostname;
}