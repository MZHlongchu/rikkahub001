package me.rerere.rikkahub.data.ai.prompts

internal val DEFAULT_DIALOGUE_COMPRESS_PROMPT = """
    你要更新一份会被继续注入主对话上下文的“连续记忆”。
    输入只有两部分：
    1. 旧的主摘要
    2. 旧主摘要之后新增的消息

    你的任务不是罗列全部历史，也不是写会议纪要。
    你的任务是把旧主摘要更新成“新的完整连续记忆”，让模型在失去长上下文之后，仍然能像同一个脑子那样继续聊天、继续写作、继续做项目。

    这份主摘要直接服务下一轮回复，所以必须优先保住：
    - 当前目标是否发生变化
    - 活跃约束、明确禁止、口径修正
    - 尚未解决的关键问题
    - 最近真正推进了什么
    - 下一步最自然该接什么
    - 一旦丢失就会明显失真的关键锚点

    必须遵守以下规则：
    1. 只输出纯文本，不要 markdown 代码块，不要解释你的压缩过程。
    2. 旧主摘要是当前仍然生效的连续记忆底稿，不是参考附录。新增消息会修改、覆盖、补强这份底稿。
    3. 输出必须是“更新后的完整主摘要”，不是在旧摘要后面追加附录，也不是把新增消息简单重写一遍。
    4. 不要为了看起来整洁，就把仍会影响下一轮回复的鲜活细节抽空。
    5. 不要把主摘要写成记忆账本、事实目录或审计记录；它首先要能续聊、续写、续做事。
    6. 对仍然影响后续回复的精确内容要高保真保留，例如：路径、命令、端口、报错、代码标识符、配置键、作品中的原句、人物口头禅、情绪触发点、用户的明确否定或改口。
    7. 信息不确定时必须显式标记【未确认】或【待验证】，不得脑补成既成事实。
    8. 允许压缩重复表达，允许折叠已经过时且不再影响后续的枝节，但不能优先删除关键锚点、未决问题、当前目标、活跃约束、最近推进或下一步。
    9. 删减顺序固定为：
       - 先删重复内容
       - 再删已经过时、已解决且不再影响后续的旧内容
       - 再压缩低价值背景
       - 最后才允许裁掉优先级较低的锚点
       - 不允许把“当前工作态”和“关键锚点”一起削平
    10. 如果对话同时包含多个主题，只保留仍然影响下一轮回复的主线；但只要某条支线仍会继续影响后续，就要留下最小必要锚点。

    你必须严格使用以下固定结构与标题：
    [当前工作态]
    当前目标：
    活跃约束：
    未决问题：
    最近推进：
    下一步：

    [连续主线]
    - 使用项目符号

    [时间推进]
    - 使用项目符号

    [关键锚点]
    - 使用项目符号

    结构要求：
    - 各字段优先写短行或短项目，不要写松散长段落。
    - 某字段没有可靠内容时，写“暂无新增确认。”，不要留空，更不要编造。
    - [连续主线] 负责保留仍在延续的任务线、关系线、写作线、对话回调点。
    - [时间推进] 负责保留发生顺序、最近转折、状态变化，不能把强时序内容压成静态标签堆。
    - [关键锚点] 只保留以后难以准确重建的原句、命令、路径、标识符、报错、风格锚点；宁缺毋滥，但绝不能敷衍。

    场景自适应要求：
    - 如果这是项目/开发对话，优先保留：repo 或工作区状态、当前任务、最近命令与结果、错误演进、关键文件、关键决策、下一步操作。
    - 如果这是长篇小说/创作对话，优先保留：场景状态、人物状态、关系变化、伏笔、世界规则、叙事推进、文风约束、必须保留的原句或意象。
    - 如果这是深度聊天，优先保留：稳定偏好、边界、核心观点、情绪上下文、仍未说完的线索、需要回调的具体表达。
    - 如果多种模式并存，以“最直接影响下一轮回复的内容”为最高优先级，同时给其他模式保留最小必要锚点。

    预算提醒只用于帮助你判断删减顺序，不是要求你机械追求长度：
    - incremental_input_tokens: {incremental_input_tokens}
    - target_output_tokens: {target_output_tokens}
    - hard_cap_tokens: {hard_cap_tokens}
    如果必须继续收缩，只能按前述删减顺序处理，不能一句“简化概括”就把关键内容抹平。
    {additional_context}

    <previous_primary_summary>
    {dialogue_summary_text}
    </previous_primary_summary>

    <incremental_messages>
    {incremental_messages}
    </incremental_messages>

    输出语言请与当前对话语境保持一致（locale: {locale}）。
""".trimIndent()

internal val DEFAULT_MEMORY_LEDGER_PROMPT = """
    你要维护一份“记忆账本”，它只服务历史对话参考工具链：
    recall_memory -> search_source -> read_source

    这份账本不是主对话摘要，也不是原文替代品。
    它的职责是帮助模型判断：
    - 哪些历史片段值得回看
    - 应该回看哪些主题线、对象、命令、路径、报错、作品片段
    - 回看时应先去哪里找接近原文的线索

    因此你必须只基于：
    1. 旧账本
    2. 本次新增消息
    来更新账本。
    不要引入不存在的信息，不要把账本写成散文摘要，也不要试图替代 read_source 返回的原始消息全文。

    输出要求：
    1. 只能输出 JSON，不要 markdown，不要代码块，不要解释。
    2. 顶层 schema 必须严格保持为：
       {
         "meta": {
           "schema_version": 3,
           "summary_turn": number,
           "updated_at": epoch_millis
         },
         "facts": [entry],
         "preferences": [entry],
         "tasks": [entry],
         "decisions": [entry],
         "constraints": [entry],
         "open_questions": [entry],
         "artifacts": [entry],
         "timeline": [entry],
         "chronology": [chronology_episode],
         "detail_capsules": [detail_capsule]
       }
    3. 普通 entry 的字段必须严格保持：
       {
         "id": string,
         "text": string,
         "status": "active" | "done" | "blocked" | "superseded" | "historical",
         "tags": [string],
         "entity_keys": [string],
         "salience": 0..1,
         "updated_at_turn": number,
         "source_roles": ["user" | "assistant", ...],
         "task_state": string?,
         "reason": string?,
         "related_ids": [string]?,
         "scope": string?,
         "blocker": string?,
         "kind": string?,
         "locator": string?,
         "change_type": string?,
         "time_ref": string?
       }
    4. chronology_episode 的字段必须严格保持：
       {
         "id": string,
         "turn_range": string,
         "summary": string,
         "source_roles": ["user" | "assistant", ...],
         "time_ref": string?,
         "related_detail_ids": [string],
         "salience": 0..1
       }
    5. detail_capsule 的字段必须严格保持：
       {
         "id": string,
         "kind": "tool" | "code" | "poem" | "quote" | "longform",
         "title": string,
         "summary": string,
         "key_excerpt": string,
         "identifiers": [string],
         "source_roles": ["user" | "assistant", ...],
         "source_message_ids": [string],
         "locator": string?,
         "updated_at_turn": number,
         "salience": 0..1,
         "status": "active" | "done" | "blocked" | "superseded" | "historical"
       }

    账本维护规则：
    6. 优先保留可检索、可定位、可回溯的原子信息；不要把多个无关事实糊成一句大总结。
    7. 强化精确锚点：实体名、文件路径、命令、配置键、端口、报错、作品标题、角色名、主题线、原句摘录。
    8. 当新信息覆盖旧信息时，保留当前有效版本，并把旧版本转为 superseded 或 historical，而不是直接抹掉。
    9. chronology 要保留事件顺序；detail_capsules 要承接较长文本、代码、诗歌、引用、工具结果等高价值原文线索。
    10. 可以压缩低检索价值的叙事性冗余，但不能牺牲后续“定位原文”的导航能力。
    11. 信息不确定时使用保守写法，不要脑补。
    12. 预算提醒：
        - incremental_input_tokens: {incremental_input_tokens}
        - minimum output tokens: {min_output_tokens}
        - target output tokens: {target_output_tokens}
        - hard cap tokens: {hard_cap_tokens}
        - minimum chronology items: {min_chronology_items}
        - minimum detail capsules: {min_detail_capsules}
    13. 如果必须继续收缩，按这个顺序处理：
        - 先合并最旧、最弱的 chronology
        - 再压缩低价值 timeline
        - 再压缩低价值 open_questions
        - 再压缩低价值 detail_capsules
        - 尽量保护 constraints、artifacts、decisions、当前有效 facts，以及带有明确 locator / identifiers / source_message_ids 的条目
    {additional_context}

    <current_memory_ledger_json>
    {rolling_summary_json}
    </current_memory_ledger_json>

    <incremental_messages>
    {incremental_messages}
    </incremental_messages>

    输出语言、命名和措辞请与当前对话语境保持一致（locale: {locale}），但始终优先保证可检索性与可定位性。
""".trimIndent()

internal val DEFAULT_MEMORY_LEDGER_PATCH_PROMPT = """
    你要输出一个“账本增量补丁”，用于在旧账本上执行局部更新。
    目标只有一个：在不降低账本质量的前提下，少写那些没有变化的部分。

    重要约束：
    1. 你维护的是历史回溯导航账本，不是主对话摘要。
    2. 输入只有旧账本和本次新增消息。
    3. 不要输出完整账本；只输出补丁 JSON。
    4. 不要删除整个 section，不要重写未改动的大段旧内容，不要修改不存在的 id。
    5. 如果你判断这次变化太大、局部更新不可靠，就输出一个空补丁：
       {
         "add_entries": [],
         "update_entries": [],
         "supersede_entries": [],
         "append_chronology": [],
         "add_or_update_detail_capsules": [],
         "update_meta": null
       }

    补丁 schema 必须严格保持：
    {
      "add_entries": [
        {
          "section_key": string,
          "entry": rolling_summary_entry
        }
      ],
      "update_entries": [
        {
          "section_key": string,
          "entry": rolling_summary_entry
        }
      ],
      "supersede_entries": [
        {
          "section_key": string,
          "entry_id": string,
          "status": "superseded" | "historical" | "done" | "blocked",
          "reason": string?,
          "related_ids": [string]
        }
      ],
      "append_chronology": [
        {
          "episode": chronology_episode
        }
      ],
      "add_or_update_detail_capsules": [
        {
          "capsule": detail_capsule
        }
      ],
      "update_meta": {
        "summary_turn": number?,
        "updated_at": epoch_millis?
      } | null
    }

    section_key 只允许使用：
    - facts
    - preferences
    - tasks
    - decisions
    - constraints
    - open_questions
    - artifacts
    - timeline

    选择 patch 而不是空补丁时，必须满足：
    - 只更新真正发生变化的条目
    - 追加新增 chronology
    - 为重要长文本、代码、工具输出补 detail capsule
    - 为后续定位原文要保住 locator、identifiers、source_message_ids、tags、entity_keys

    {additional_context}

    <current_memory_ledger_json>
    {rolling_summary_json}
    </current_memory_ledger_json>

    <incremental_messages>
    {incremental_messages}
    </incremental_messages>

    只输出补丁 JSON。
""".trimIndent()

internal val DEFAULT_COMPRESS_PROMPT = DEFAULT_MEMORY_LEDGER_PROMPT
