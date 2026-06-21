package com.twilio.agentconnect.tools.builtin;


import com.twilio.agentconnect.context.client.KnowledgeClient;
import com.twilio.agentconnect.context.model.MessageContext;
import com.twilio.agentconnect.session.Session;
import com.twilio.agentconnect.tools.InjectContext;
import com.twilio.agentconnect.tools.InjectSession;
import com.twilio.agentconnect.tools.TacTool;
import com.twilio.agentconnect.tools.TacToolParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Built-in tool for searching knowledge store.
 */
@Component
public class KnowledgeSearchTool {

    private final KnowledgeClient knowledgeClient;

    public KnowledgeSearchTool(KnowledgeClient knowledgeClient) {
        this.knowledgeClient = knowledgeClient;
    }

    @TacTool(description = "Search the knowledge base for relevant information")
    public Mono<String> searchKnowledge(
            @TacToolParam(description = "Search query") String query,
            @TacToolParam(description = "Maximum number of results", required = false) Integer limit,
            @InjectSession Session session,
            @InjectContext MessageContext context) {

        int maxResults = limit != null ? limit : 5;

        return knowledgeClient.search(query, maxResults)
            .map(results -> {
                if (results.isEmpty()) {
                    return "No knowledge articles found for query: " + query;
                }

                StringBuilder result = new StringBuilder();
                result.append("Knowledge Search Results:\n\n");

                for (int i = 0; i < results.size(); i++) {
                    var item = results.get(i);
                    result.append(i + 1).append(". ");
                    result.append(item.getOrDefault("title", "Untitled")).append("\n");
                    result.append("   ").append(item.getOrDefault("content", "")).append("\n\n");
                }

                return result.toString();
            });
    }
}
