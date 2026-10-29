import { useMutation, useQuery } from '@tanstack/react-query';
import aggregatorService from '../services/aggregatorService.js';

export const useAggregatorSources = () =>
 useQuery({
 queryKey: ['aggregator', 'sources'],
 queryFn: aggregatorService.sources,
 staleTime: 10 * 60 * 1000,
 retry: 1,
 });

export const useAggregatorSearch = () =>
 useMutation({
 mutationKey: ['aggregator', 'search'],
 mutationFn: aggregatorService.search,
 });

export const useShareListing = () =>
 useMutation({
 mutationKey: ['aggregator', 'submit'],
 mutationFn: aggregatorService.submit,
 });
