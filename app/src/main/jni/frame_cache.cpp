#include "frame_cache.h"
#include "frame_pool.h"

namespace montage {

void FrameCache::releaseSlot(const Node& node) {
    if (pool_ && node.frame.poolSlot >= 0) pool_->release(node.frame.poolSlot);
}

void FrameCache::put(int64_t key, const Frame& frame, uint32_t generation) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = index_.find(key);
    if (it != index_.end()) {
        if (it->second->frame.poolSlot != frame.poolSlot) releaseSlot(*it->second);
        it->second->frame = frame;
        it->second->generation = generation;
        lru_.splice(lru_.begin(), lru_, it->second);
        return;
    }
    if (lru_.size() >= capacity_) {
        releaseSlot(lru_.back());
        index_.erase(lru_.back().key);
        lru_.pop_back();
    }
    lru_.push_front({key, frame, generation});
    index_[key] = lru_.begin();
}

bool FrameCache::get(int64_t key, uint32_t generation, Frame& out) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = index_.find(key);
    if (it == index_.end() || it->second->generation != generation) return false;
    lru_.splice(lru_.begin(), lru_, it->second);
    out = it->second->frame;
    return true;
}

void FrameCache::evictAll() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (const Node& n : lru_) releaseSlot(n);
    lru_.clear();
    index_.clear();
}

void FrameCache::setCapacity(size_t n) {
    std::lock_guard<std::mutex> lock(mutex_);
    capacity_ = n;
    while (lru_.size() > capacity_) {
        releaseSlot(lru_.back());
        index_.erase(lru_.back().key);
        lru_.pop_back();
    }
}

} // namespace montage
