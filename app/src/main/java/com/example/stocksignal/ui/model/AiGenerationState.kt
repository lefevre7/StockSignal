package com.example.stocksignal.ui.model

/**
 * Represents the state of AI score generation
 */
enum class AiGenerationState {
    /** No AI generation in progress */
    IDLE,
    
    /** AI generation request is queued but not yet started */
    QUEUED,
    
    /** AI generation is actively in progress */
    GENERATING,
    
    /** AI generation completed successfully */
    COMPLETE,
    
    /** AI generation failed with an error */
    ERROR
}
