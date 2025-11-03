package me.ixor.sred.policy

import me.ixor.sred.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path

/**
 * 策略加载器测试
 */
class PolicyLoaderTest {
    
    @Test
    fun `test load policies from YAML string`() = runBlocking {
        val yaml = """
            policies:
              - id: test_policy_1
                name: Test Policy 1
                description: Test policy
                version: "1.0"
                enabled: true
                priority: 10
                rules:
                  - name: Allow rule
                    type: ALLOW
                    condition:
                      type: always
                    action:
                      type: SET_COMPLIANCE_SCORE
                      score: 0.9
        """.trimIndent()
        
        val policies = PolicyLoader.fromYamlString(yaml)
        
        assertEquals(1, policies.size)
        assertEquals("test_policy_1", policies[0].id)
        assertEquals("Test Policy 1", policies[0].name)
        assertEquals("1.0", policies[0].version)
        assertTrue(policies[0].enabled)
        assertEquals(10, policies[0].priority)
        assertEquals(1, policies[0].rules.size)
    }
    
    @Test
    fun `test load policies from JSON string`() = runBlocking {
        val json = """
            {
              "policies": [
                {
                  "id": "test_policy_2",
                  "name": "Test Policy 2",
                  "description": "Test policy",
                  "version": "1.0",
                  "enabled": true,
                  "priority": 20,
                  "rules": [
                    {
                      "name": "Deny rule",
                      "type": "DENY",
                      "condition": {
                        "type": "always"
                      },
                      "action": {
                        "type": "SET_COMPLIANCE_SCORE",
                        "score": 0.1
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        
        val policies = PolicyLoader.fromJsonString(json)
        
        assertEquals(1, policies.size)
        assertEquals("test_policy_2", policies[0].id)
        assertEquals(1, policies[0].rules.size)
    }
    
    @Test
    fun `test load policies from YAML file`(@TempDir tempDir: Path) = runBlocking {
        val yamlFile = tempDir.resolve("policies.yaml").toFile()
        yamlFile.writeText("""
            policies:
              - id: file_policy
                name: File Policy
                version: "1.0"
                rules: []
        """.trimIndent())
        
        val policies = PolicyLoader.fromYaml(yamlFile.absolutePath)
        
        assertEquals(1, policies.size)
        assertEquals("file_policy", policies[0].id)
    }
    
    @Test
    fun `test load policies from JSON file`(@TempDir tempDir: Path) = runBlocking {
        val jsonFile = tempDir.resolve("policies.json").toFile()
        jsonFile.writeText("""
            {
              "policies": [
                {
                  "id": "json_policy",
                  "name": "JSON Policy",
                  "version": "1.0",
                  "rules": []
                }
              ]
            }
        """.trimIndent())
        
        val policies = PolicyLoader.fromJson(jsonFile.absolutePath)
        
        assertEquals(1, policies.size)
        assertEquals("json_policy", policies[0].id)
    }
    
    @Test
    fun `test policy with state-based condition`() = runBlocking {
        val yaml = """
            policies:
              - id: state_policy
                name: State Policy
                version: "1.0"
                rules:
                  - name: State rule
                    type: ALLOW
                    condition:
                      type: state
                      toStates: ["state_b", "state_c"]
                    action:
                      type: SET_COMPLIANCE_SCORE
                      score: 0.8
        """.trimIndent()
        
        val policies = PolicyLoader.fromYamlString(yaml)
        
        assertEquals(1, policies.size)
        val rule = policies[0].rules[0]
        assertTrue(rule.condition is RuleCondition.StateBased)
        val stateCondition = rule.condition as RuleCondition.StateBased
        assertEquals(setOf("state_b", "state_c"), stateCondition.toStates)
    }
    
    @Test
    fun `test policy with context-based condition`() = runBlocking {
        val yaml = """
            policies:
              - id: context_policy
                name: Context Policy
                version: "1.0"
                rules:
                  - name: Context rule
                    type: ALLOW
                    condition:
                      type: context
                      expression: "status == ready"
                    action:
                      type: SET_COMPLIANCE_SCORE
                      score: 0.9
        """.trimIndent()
        
        val policies = PolicyLoader.fromYamlString(yaml)
        
        assertEquals(1, policies.size)
        val rule = policies[0].rules[0]
        assertTrue(rule.condition is RuleCondition.ContextBased)
    }
    
    @Test
    fun `test policy with time range`() = runBlocking {
        val yaml = """
            policies:
              - id: time_policy
                name: Time Policy
                version: "1.0"
                effectiveTimeRange:
                  start: "2025-01-01T00:00:00Z"
                  end: "2025-12-31T23:59:59Z"
                rules: []
        """.trimIndent()
        
        val policies = PolicyLoader.fromYamlString(yaml)
        
        assertEquals(1, policies.size)
        assertNotNull(policies[0].effectiveTimeRange)
        assertNotNull(policies[0].effectiveTimeRange?.start)
        assertNotNull(policies[0].effectiveTimeRange?.end)
    }
}

/**
 * 策略文件监听器测试
 */
class PolicyWatcherTest {
    
    @Test
    fun `test policy watcher reload on file change`(@TempDir tempDir: Path) = runBlocking {
        val policyFile = tempDir.resolve("test_policies.yaml").toFile()
        policyFile.writeText("""
            policies:
              - id: initial_policy
                name: Initial Policy
                version: "1.0"
                rules: []
        """.trimIndent())
        
        val policyEngine = PolicyEngineFactory.create()
        val watcher = PolicyWatcher(policyFile.absolutePath, policyEngine)
        
        // 初始加载
        watcher.reload()
        
        val initialPolicies = policyEngine.getApplicablePolicies(StateContextFactory.create())
        assertTrue(initialPolicies.any { it.id == "initial_policy" })
        
        // 修改文件
        policyFile.writeText("""
            policies:
              - id: updated_policy
                name: Updated Policy
                version: "2.0"
                rules: []
        """.trimIndent())
        
        // 手动重载
        watcher.reload()
        
        val updatedPolicies = policyEngine.getApplicablePolicies(StateContextFactory.create())
        assertTrue(updatedPolicies.any { it.id == "updated_policy" })
        
        watcher.stop()
    }
}


