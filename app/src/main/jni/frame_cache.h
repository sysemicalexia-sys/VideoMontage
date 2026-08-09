#pragma once
#include "frame.h"
#include <list>
#include <mutex>
#include <unordered_map>

namespace montage {

class FramePool;

/** LRU cache keyed by quantized source PTS. Hit rate matters most during
 *  scrubs — quantizing to frame boundaries means a finger resting between
 *  two frames still hits. Generations flush the cache when the timeline
 *  mutates.
 *
 *  Ownership: a cached Frame keeps its pool slot until eviction — the slot
 *  is released back to the pool here, never at the call site, so cached
 *  frames never dangle. */
class FrameCache {
public:
    explicit FrameCache(size_t capacity = 24) : capacity_(capacity) {}

    void setPool(FramePool* pool) { pool_ = pool; }

    void put(int64_t quantizedPtsUs, const Frame& frame, uint32_t generation);
    bool get(int64_t quantizedPtsUs, uint32_t generation, Frame& out);
    void evictAll();
    void setCapacity(size_t n);

private:
    struct Node { int64_t key; Frame frame; uint32_t generation; };

    void releaseSlot(const Node& node);

    std::mutex mutex_;
    size_t capacity_;
    std::list<Node> lru_;
    std::unordered_map<int64_t, std::list<Node>::iterator> index_;
    FramePool* pool_ = nullptr;
};

} // namespace montage
