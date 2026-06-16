"""The warm-up interview.

Twenty serious questions, ordered to move from how you think -> what you value
-> how you feel -> how you speak. The answers seed Echo's first model of you.
Keep them open-ended; depth here is what makes everything downstream good.
"""

QUESTIONS = [
    {
        "id": "q01_deciding",
        "text": "When you face a hard decision, do you trust your gut or reason it out? Walk me through how you actually decide — step by step, honestly.",
    },
    {
        "id": "q02_contrarian",
        "text": "What's a belief or opinion you hold that a lot of people around you would disagree with? Why do you hold it anyway?",
    },
    {
        "id": "q03_anger",
        "text": "What makes you genuinely angry — not mildly annoyed, but angry? What's underneath that for you?",
    },
    {
        "id": "q04_people",
        "text": "Who are the most important people in your life right now, and what role does each one play for you?",
    },
    {
        "id": "q05_stress",
        "text": "When you're stressed or overwhelmed, what does that actually look like from the outside — and what genuinely helps you?",
    },
    {
        "id": "q06_goodlife",
        "text": "What does a good life look like to you? When you strip it back, what are you really optimising for?",
    },
    {
        "id": "q07_voice",
        "text": "How do you talk when you're fully yourself — blunt, sarcastic, warm, careful, playful? Give me a feel for your actual voice.",
    },
    {
        "id": "q08_changedmind",
        "text": "What's something important you've changed your mind about in the last few years, and what changed it?",
    },
    {
        "id": "q09_values",
        "text": "What do you value more: being right, being kind, or being free? Rank them and tell me why that order.",
    },
    {
        "id": "q10_prideregret",
        "text": "Tell me about a decision you're proud of and one you regret. What's the real difference between them?",
    },
    {
        "id": "q11_risk",
        "text": "How much risk are you comfortable with — in money, career, and relationships? Where's your line?",
    },
    {
        "id": "q12_ambition",
        "text": "What are you most ambitious about? What do you secretly want that you don't say out loud much?",
    },
    {
        "id": "q13_conflict",
        "text": "When you disagree with someone who matters, how do you handle it — push hard, go quiet, find the middle, walk away?",
    },
    {
        "id": "q14_nonnegotiables",
        "text": "What are your non-negotiables — the lines you won't cross no matter the reward?",
    },
    {
        "id": "q15_joy",
        "text": "What tends to make you happy on an ordinary, unremarkable day?",
    },
    {
        "id": "q16_future",
        "text": "Who do you want to be in five years — and who are you quietly afraid of becoming?",
    },
    {
        "id": "q17_signature",
        "text": "What's a running joke, phrase, or way of speaking that's distinctly you? How would a close friend impersonate you?",
    },
    {
        "id": "q18_advice",
        "text": "When someone asks your advice, what do you tend to tell them? What's your default piece of wisdom?",
    },
    {
        "id": "q19_avoidance",
        "text": "What do you tend to procrastinate on or avoid — and why do you think that is?",
    },
    {
        "id": "q20_speakforme",
        "text": "If I had to speak for you in a room you weren't in, what must I never get wrong about you?",
    },
]

BY_ID = {q["id"]: q for q in QUESTIONS}
